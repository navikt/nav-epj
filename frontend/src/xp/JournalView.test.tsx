import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { JournalView } from "./JournalView";
import { useJournalStore } from "./journalStore";
import { expectNoSeriousViolations } from "./axeHelper";

const patient = {
  id: "p1",
  givenName: "Matematisk",
  familyName: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const diag = { code: "A02", system: "ICPC2", text: "Frysninger" };
const a01 = { code: "A01", system: "ICPC2", text: "Smerte" };

function consultation(overrides: Record<string, unknown> = {}) {
  return {
    id: "k1",
    patientId: "p1",
    hpr: ["9144889"],
    clinicalNotes: [],
    diagnoses: [],
    startedAt: "2026-09-30T09:00:00",
    endedAt: null,
    status: "IN_PROGRESS",
    reasonForVisit: null,
    ...overrides,
  };
}

type Handler = (init?: RequestInit) => { ok?: boolean; body?: unknown };

function stub(routes: Record<string, Handler>) {
  const calls: { key: string; body?: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const key = `${init?.method ?? "GET"} ${url}`;
      calls.push({
        key,
        body: init?.body ? JSON.parse(init.body as string) : undefined,
      });
      const result =
        routes[key]?.(init) ??
        (key === "PUT /api/active-patient"
          ? { body: { patientId: "p1", expiresAt: "2026-09-30T17:14:00Z" } }
          : { ok: false });
      const ok = result.ok ?? true;
      return { ok, status: ok ? 200 : 500, json: async () => result.body };
    }),
  );
  return calls;
}

const base = {
  "GET /api/patient/p1": () => ({ body: patient }),
};

async function openJournal(consultations: unknown[], routes: Record<string, Handler> = {}) {
  const calls = stub({
    ...base,
    "GET /api/patients/p1/consultations": () => ({ body: consultations }),
    ...routes,
  });
  const view = render(<JournalView />);
  await act(async () => {
    await useJournalStore.getState().open("p1");
  });
  return { calls, ...view };
}

const savedNote = {
  id: "n1",
  consultationId: "k1",
  patientId: "p1",
  clinicalNote: "Hei",
};

describe("JournalView", () => {
  afterEach(() => {
    useJournalStore.getState().clear();
    vi.unstubAllGlobals();
  });

  it("shows a loading status until the patient and consultations have loaded", async () => {
    render(<JournalView />);
    act(() => {
      useJournalStore.setState({ patientId: "p1", status: "loading" });
    });
    expect(screen.getByRole("status")).toHaveTextContent("Laster …");
  });

  it("refreshes on visible return without overlapping requests and stops listening on unmount", async () => {
    let served: unknown[] = [consultation()];
    const { calls, unmount } = await openJournal([], {
      "GET /api/patients/p1/consultations": () => ({ body: served }),
    });
    const count = () =>
      calls.filter((c) => c.key === "GET /api/patients/p1/consultations").length;
    served = [consultation({ clinicalNotes: [savedNote] })];
    const before = count();
    await act(async () => {
      window.dispatchEvent(new Event("focus"));
      document.dispatchEvent(new Event("visibilitychange"));
    });
    expect(count()).toBe(before + 1);
    expect(useJournalStore.getState().draft.clinicalNote).toBe("Hei");
    unmount();
    window.dispatchEvent(new Event("focus"));
    document.dispatchEvent(new Event("visibilitychange"));
    expect(count()).toBe(before + 1);
  });

  it("shows a retryable alert when a focus refresh fails and keeps the journal", async () => {
    let fail = false;
    await openJournal([consultation()], {
      "GET /api/patients/p1/consultations": () =>
        fail ? { ok: false } : { body: [consultation()] },
    });
    fail = true;
    await act(async () => {
      window.dispatchEvent(new Event("focus"));
    });
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Kunne ikke oppdatere konsultasjoner");
    fail = false;
    await userEvent.click(within(alert).getByRole("button", { name: "Prøv igjen" }));
    await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  });

  it("shows a retryable alert when loading failed", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ ok: false }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
    });
    render(<JournalView />);
    await act(async () => {
      await useJournalStore.getState().open("p1");
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Feil ved lasting av pasient",
    );
    stub({ ...base, "GET /api/patients/p1/consultations": () => ({ body: [] }) });
    await userEvent.click(screen.getByRole("button", { name: "Prøv igjen" }));
    expect(await screen.findByRole("region", { name: "Pasientkontekst" })).toBeInTheDocument();
    expect(calls.length).toBeGreaterThan(0);
  });

  it("offers to start a consultation when none is ongoing and shows the form after starting", async () => {
    const started = consultation();
    const { container } = await openJournal([], {
      "POST /api/patients/p1/consultations": () => ({ body: started }),
    });
    expect(
      await screen.findByRole("heading", { name: "Ingen pågående konsultasjon" }),
    ).toBeInTheDocument();
    await expectNoSeriousViolations(container);
    await userEvent.click(screen.getByRole("button", { name: "Start konsultasjon" }));
    expect(
      await screen.findByRole("heading", { name: "Pågående konsultasjon" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Konsultasjon" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("preloads the main and secondary diagnoses and the note and passes axe", async () => {
    const { container } = await openJournal([
      consultation({ diagnoses: [diag, a01], clinicalNotes: [savedNote] }),
    ]);
    expect(await screen.findByLabelText("Journalnotat")).toHaveValue("Hei");
    const main = screen.getByRole("list", { name: "Valgt hoveddiagnose" });
    expect(within(main).getByText("Frysninger")).toBeInTheDocument();
    expect(within(main).getByText("· lagret")).toBeInTheDocument();
    const secondary = screen.getByRole("list", { name: "Valgte bidiagnoser" });
    expect(within(secondary).getByText("Smerte")).toBeInTheDocument();
    expect(within(secondary).getByText("· lagret")).toBeInTheDocument();
    expect(screen.getByText(/^◐ Pågående · \d+ min$/)).toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("blocks Lagre and Fullfør konsultasjon without a request when only bidiagnoser remain", async () => {
    const { calls } = await openJournal([
      consultation({ diagnoses: [diag, a01], clinicalNotes: [savedNote] }),
    ]);
    await userEvent.click(await screen.findByRole("button", { name: "Fjern A02 Frysninger" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Velg en hoveddiagnose");
    expect(screen.getByRole("list", { name: "Valgte bidiagnoser" })).toHaveTextContent("Smerte");
    expect(screen.getByText("Ingen hoveddiagnose valgt.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Lagre" }));
    await userEvent.click(screen.getByRole("button", { name: "Fullfør konsultasjon" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls.some((c) => c.key.startsWith("PATCH"))).toBe(false);
  });

  it("marks unsaved changes in the status and the sub-tab, and Lagre sends null for a blank note", async () => {
    const { calls } = await openJournal([consultation()], {
      "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
    });
    const note = await screen.findByLabelText("Journalnotat");
    await userEvent.type(note, "Ny");
    expect(screen.getByText("● Ulagrede endringer")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Konsultasjon •" })).toBeInTheDocument();
    await userEvent.clear(note);
    expect(screen.queryByText("● Ulagrede endringer")).not.toBeInTheDocument();
    await userEvent.type(note, "x");
    await userEvent.clear(note);
    await userEvent.click(screen.getByRole("button", { name: "Lagre" }));
    const patch = calls.find((c) => c.key.startsWith("PATCH"));
    expect(patch?.body).toMatchObject({ consultationId: "k1", clinicalNote: null, finalize: false });
  });

  it("reports a save error with a retry that saves again", async () => {
    let fail = true;
    await openJournal([consultation()], {
      "PATCH /api/patients/p1/consultations": () =>
        fail ? { ok: false } : { body: {} },
    });
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    await userEvent.click(screen.getByRole("button", { name: "Lagre" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "Konsultasjonen ble ikke lagret. Endringene dine er ikke borte.",
    );
    expect(screen.getByText("✖ Ikke lagret")).toBeInTheDocument();
    fail = false;
    await userEvent.click(within(alert).getByRole("button", { name: "Prøv igjen" }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    expect(screen.getByText(/^✔ Lagret kl\. /)).toBeInTheDocument();
  });

  it("does not open the finish dialog while a save is in flight", async () => {
    let releasePatch: () => void = () => {};
    await openJournal([consultation()], {
      "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
    });
    const fetchMock = vi.mocked(fetch);
    const original = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation(async (url, init) => {
      if (init?.method === "PATCH") {
        await new Promise<void>((resolve) => {
          releasePatch = resolve;
        });
      }
      return original(url, init);
    });
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "Hei");
    await userEvent.click(screen.getByRole("button", { name: "Lagre" }));
    await userEvent.click(screen.getByRole("button", { name: "Fullfør konsultasjon" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await act(async () => releasePatch());
  });

  it("asks for confirmation before finishing and shows the read-only summary afterwards", async () => {
    const { calls } = await openJournal(
      [consultation({ diagnoses: [diag], clinicalNotes: [savedNote] })],
      {
        "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
      },
    );
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "!");
    await userEvent.click(screen.getByRole("button", { name: "Fullfør konsultasjon" }));
    const dialog = screen.getByRole("alertdialog", {
      name: "Fullføre konsultasjonen for Matematisk Ape?",
    });
    expect(within(dialog).getByText("Ulagrede endringer lagres samtidig.")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls.some((c) => c.key.startsWith("PATCH"))).toBe(false);

    await userEvent.click(screen.getByRole("button", { name: "Fullfør konsultasjon" }));
    const again = screen.getByRole("alertdialog");
    stubFinished();
    await userEvent.click(within(again).getByRole("button", { name: "Fullfør konsultasjon" }));
    expect(
      await screen.findByRole("heading", { name: "Fullført konsultasjon" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/skrivebeskyttet/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Journalnotat")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start konsultasjon" })).toBeInTheDocument();
  });

  it("asks for confirmation before cancelling and returns to the no-consultation state afterwards", async () => {
    const { calls } = await openJournal(
      [consultation({ diagnoses: [diag], clinicalNotes: [savedNote] })],
      {
        "POST /api/patients/p1/consultations/k1/cancel": () => ({ body: {} }),
      },
    );
    await userEvent.type(await screen.findByLabelText("Journalnotat"), "!");
    await userEvent.click(screen.getByRole("button", { name: "Slett" }));
    const dialog = screen.getByRole("alertdialog", {
      name: "Avlyse konsultasjonen for Matematisk Ape?",
    });
    expect(within(dialog).getByText("Ulagrede endringer går tapt.")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls.some((c) => c.key.includes("cancel"))).toBe(false);

    await userEvent.click(screen.getByRole("button", { name: "Slett" }));
    const again = screen.getByRole("alertdialog");
    stub({
      ...base,
      "POST /api/patients/p1/consultations/k1/cancel": () => ({ body: {} }),
      "GET /api/patients/p1/consultations": () => ({
        body: [
          consultation({
            status: "CANCELLED",
            endedAt: "2026-09-30T09:20:00",
            diagnoses: [diag],
            clinicalNotes: [savedNote],
          }),
        ],
      }),
    });
    await userEvent.click(within(again).getByRole("button", { name: "Slett" }));
    expect(
      await screen.findByRole("heading", { name: "Ingen pågående konsultasjon" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Start konsultasjon" })).toBeInTheDocument();
  });

  it("lists a cancelled consultation as Avlyst in Tidligere konsultasjoner", async () => {
    await openJournal([
      consultation(),
      consultation({
        id: "k0",
        status: "CANCELLED",
        startedAt: "2026-09-01T09:00:00",
        endedAt: "2026-09-01T09:10:00",
      }),
    ]);
    await userEvent.click(await screen.findByRole("tab", { name: "Tidligere konsultasjoner (1)" }));
    const table = screen.getByRole("table", { name: "Tidligere konsultasjoner (1)" });
    expect(within(table).getByText("✖ Avlyst")).toBeInTheDocument();
  });

  it("lists previous consultations and shows the selected one read-only", async () => {
    await openJournal([
      consultation(),
      consultation({
        id: "k0",
        status: "FINISHED",
        startedAt: "2026-09-01T09:00:00",
        endedAt: "2026-09-01T09:30:00",
        diagnoses: [diag, a01],
        clinicalNotes: [{ ...savedNote, id: "n0", consultationId: "k0", clinicalNote: "Gammelt notat" }],
      }),
    ]);
    await userEvent.click(await screen.findByRole("tab", { name: "Tidligere konsultasjoner (1)" }));
    const table = screen.getByRole("table", { name: "Tidligere konsultasjoner (1)" });
    const row = within(table).getByRole("button", { name: "01.09.2026 09:00" }).closest("tr")!;
    const cells = within(row).getAllByRole("cell");
    expect(cells[2]).toHaveTextContent("A02 (ICPC-2)");
    expect(cells[3]).toHaveTextContent("A01 (ICPC-2)");
    expect(within(table).getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Dato",
      "Status",
      "Hoveddiagnose",
      "Bidiagnoser",
    ]);
    expect(within(table).queryByText("◐ Pågående")).not.toBeInTheDocument();
    expect(within(table).getByText("✔ Fullført")).toBeInTheDocument();
    expect(screen.getByText("Velg en konsultasjon for å se diagnoser og journalnotater.")).toBeInTheDocument();
    await userEvent.click(within(table).getByRole("button", { name: "01.09.2026 09:00" }));
    expect(screen.getByRole("heading", { name: "Konsultasjon 01.09.2026 09:00 (skrivebeskyttet)" })).toBeInTheDocument();
    expect(screen.getByText("Gammelt notat")).toBeInTheDocument();
    expect(screen.getByText("Hoveddiagnose", { selector: "dt" }).nextElementSibling).toHaveTextContent(
      "A02 Frysninger · ICPC-2",
    );
    expect(screen.getByText("Bidiagnoser", { selector: "dt" }).nextElementSibling).toHaveTextContent(
      "A01 Smerte · ICPC-2",
    );
    expect(screen.getByText("01.09.2026 09:30")).toBeInTheDocument();
  });

  it("never lists the ongoing consultation under Tidligere konsultasjoner", async () => {
    await openJournal([consultation()]);
    await userEvent.click(await screen.findByRole("tab", { name: "Tidligere konsultasjoner (0)" }));
    expect(screen.getByText("Ingen tidligere konsultasjoner.")).toBeInTheDocument();
  });

  it("shows an empty state when there are no previous consultations", async () => {
    await openJournal([]);
    await userEvent.click(await screen.findByRole("tab", { name: "Tidligere konsultasjoner (0)" }));
    expect(screen.getByText("Ingen tidligere konsultasjoner.")).toBeInTheDocument();
  });

  it("keeps the journal usable when the measurements request fails", async () => {
    await openJournal([consultation()], { "GET /api/patient/p1/measurements": () => ({ ok: false }) });
    await userEvent.click(await screen.findByRole("tab", { name: "Målinger" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Feil ved lasting av målinger");
    expect(screen.getByText(/Ape/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "Konsultasjon" }));
    expect(screen.getByRole("tab", { name: "Konsultasjon" })).toHaveAttribute("aria-selected", "true");
  });
});

function stubFinished() {
  stub({
    ...base,
    "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
    "GET /api/patients/p1/consultations": () => ({
      body: [
        consultation({
          status: "FINISHED",
          endedAt: "2026-09-30T09:20:00",
          diagnoses: [diag],
          clinicalNotes: [{ ...savedNote, clinicalNote: "Hei!" }],
        }),
      ],
    }),
  });
}
