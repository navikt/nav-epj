import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NewPatientDialog } from "./NewPatientDialog";
import { CurrentUserContext } from "./currentUser";
import { useBalloonStore } from "./balloonStore";
import { expectNoSeriousViolations } from "./axeHelper";

const created = {
  id: "p9",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

function stubFetch(handler: (url: string, init?: RequestInit) => unknown) {
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const result = handler(url, init) as { ok: boolean; body?: unknown };
    return { ok: result.ok, status: result.ok ? 200 : 400, json: async () => result.body };
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

function setup() {
  const onClose = vi.fn();
  const view = render(
    <CurrentUserContext.Provider
      value={{ navn: "Lege Legesen", autorisasjon: "Lege", legekontor: "Kontor" }}
    >
      <NewPatientDialog onClose={onClose} />
    </CurrentUserContext.Provider>,
  );
  return { ...view, onClose };
}

async function fill(user: ReturnType<typeof userEvent.setup>, fnr = "01019012345") {
  await user.type(screen.getByLabelText("Fornavn"), "Matematisk");
  await user.type(screen.getByLabelText("Etternavn"), "Ape");
  await user.type(screen.getByLabelText("Fødselsnummer"), fnr);
}

describe("NewPatientDialog", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders a labelled dialog with intro, local-only badge and focus in the first field", async () => {
    const { baseElement } = setup();
    expect(screen.getByRole("dialog", { name: "Ny pasient" })).toBeInTheDocument();
    expect(
      screen.getByText("Pasienten knyttes til deg (Lege Legesen). Bruk bare syntetiske fødselsnumre."),
    ).toBeInTheDocument();
    expect(screen.getByText("⚙ Kun lokalt")).toBeInTheDocument();
    expect(screen.getByLabelText("Fornavn")).toHaveFocus();
    expect(screen.getByLabelText("Fødselsnummer")).toHaveAccessibleDescription(
      "11 siffer, for eksempel et Tenor-nummer",
    );
    await expectNoSeriousViolations(baseElement);
  });

  it("validates on submit, shows all errors and focuses the first one", async () => {
    const fn = stubFetch(() => ({ ok: true, body: created }));
    const user = userEvent.setup();
    const { baseElement } = setup();
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(screen.getByText(/Fornavn er påkrevd/)).toBeInTheDocument();
    expect(screen.getByText(/Etternavn er påkrevd/)).toBeInTheDocument();
    expect(screen.getByText(/Fødselsnummer må bestå av 11 siffer/)).toBeInTheDocument();
    expect(screen.getByLabelText("Fornavn")).toHaveFocus();
    expect(screen.getByLabelText("Fornavn")).toHaveAttribute("aria-invalid", "true");
    expect(fn).not.toHaveBeenCalled();
    await expectNoSeriousViolations(baseElement);
  });

  it("focuses the fødselsnummer when only that is invalid", async () => {
    const user = userEvent.setup();
    setup();
    await fill(user, "123");
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(screen.getByLabelText("Fødselsnummer")).toHaveFocus();
  });

  it("rejects eleven digits that are not a valid date", async () => {
    const user = userEvent.setup();
    setup();
    await fill(user, "99999999999");
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(screen.getByText(/Fødselsnummer må bestå av 11 siffer/)).toBeInTheDocument();
  });

  it("posts the derived fields, shows a balloon and closes on success", async () => {
    const fn = stubFetch(() => ({ ok: true, body: created }));
    const user = userEvent.setup();
    const { onClose } = setup();
    await fill(user);
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const post = fn.mock.calls.find(([, init]) => init?.method === "POST")!;
    expect(post[0]).toBe("/api/patient");
    expect(JSON.parse(post[1]!.body as string)).toEqual({
      fornavn: "Matematisk",
      etternavn: "Ape",
      personident: "01019012345",
      personidentType: "FNR",
      birthDate: "1990-01-01",
      gender: "MALE",
    });
    expect(useBalloonStore.getState().balloon?.title).toBe("Pasient lagret");
  });

  it("disables the form and shows progress while saving", async () => {
    let release: (value: unknown) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise((resolve) => {
            release = resolve;
          }),
      ),
    );
    const user = userEvent.setup();
    const { onClose } = setup();
    await fill(user);
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(screen.getByText("Lagrer pasient …", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByLabelText("Fornavn")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Lagrer …" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(onClose).not.toHaveBeenCalled();
    release({ ok: true, status: 200, json: async () => created });
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it("shows a server error and re-enables the form", async () => {
    stubFetch(() => ({ ok: false }));
    const user = userEvent.setup();
    const { onClose } = setup();
    await fill(user);
    await user.click(screen.getByRole("button", { name: "Lagre pasient" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Kunne ikke opprette pasient. Kontroller opplysningene eller sjekk om pasienten finnes fra før.",
    );
    expect(screen.getByLabelText("Fornavn")).toBeEnabled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("closes on cancel and Escape", async () => {
    const user = userEvent.setup();
    const { onClose } = setup();
    await user.click(screen.getByRole("button", { name: "Avbryt" }));
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
