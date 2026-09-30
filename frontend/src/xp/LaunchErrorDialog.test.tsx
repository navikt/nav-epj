import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LaunchErrorDialog } from "./LaunchErrorDialog";
import type { AppDialog, AppErrorCode } from "./appDialogStore";
import { seedApps, seedJournal, seedRun, sykInn } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { useBalloonStore } from "./balloonStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { useWorkspaceStore } from "./workspaceStore";

const navigate = vi.fn();

vi.mock("@tanstack/react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@tanstack/react-router")>()),
  useNavigate: () => navigate,
}));

type ErrorDialog = Extract<AppDialog, { kind: "error" }>;

function dialogOf(code: AppErrorCode, patch: Partial<ErrorDialog> = {}) {
  return {
    kind: "error",
    code,
    clientId: "syk-inn",
    app: "Sykmelding",
    patientName: "Ola Nordmann",
    status: 409,
    call: "POST /api/launch",
    at: new Date(2026, 8, 30, 9, 14, 5),
    ...patch,
  } satisfies ErrorDialog;
}

function setup(dialog: ErrorDialog) {
  const onClose = vi.fn();
  const view = render(<LaunchErrorDialog dialog={dialog} onClose={onClose} />);
  return { ...view, onClose };
}

const primaryLabel = (code: AppErrorCode) => copy[`s8.${code}.action`];

describe("LaunchErrorDialog", () => {
  beforeEach(() => {
    navigate.mockReset();
    seedApps();
    seedJournal();
  });

  it.each([
    ["NO_ACTIVE_PATIENT", copy["s8.title.cannotStart"]("Sykmelding")],
    ["NO_ACTIVE_ENCOUNTER", copy["s8.title.cannotStart"]("Sykmelding")],
    ["PATIENT_MISMATCH", copy["s8.title.cannotStart"]("Sykmelding")],
    ["UNKNOWN_APP", copy["s8.title.cannotStart"]("Sykmelding")],
    ["FRAMING_REFUSED", copy["s8.FRAMING_REFUSED.title"]],
    ["SESSION_EXPIRED", copy["s8.SESSION_EXPIRED.title"]],
    ["NETWORK", copy["s8.NETWORK.title"]],
  ] as const)("shows title and heading for %s", (code, title) => {
    setup(dialogOf(code));
    const dialog = screen.getByRole("alertdialog", {
      name: copy[`s8.${code}.head`],
    });
    expect(dialog).toHaveTextContent(title);
  });

  it("names patient and app in the encounter message", () => {
    setup(dialogOf("NO_ACTIVE_ENCOUNTER"));
    expect(
      screen.getByText(copy["s8.NO_ACTIVE_ENCOUNTER.body"]("Sykmelding", "Ola Nordmann")),
    ).toBeInTheDocument();
  });

  it("keeps technical details collapsed and lists them when expanded", async () => {
    setup(dialogOf("NO_ACTIVE_ENCOUNTER"));
    const toggle = screen.getByRole("button", { name: copy["s8.details"] });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("NO_ACTIVE_ENCOUNTER", { selector: "dd" })).toBeVisible();
    expect(screen.getByText("syk-inn", { selector: "dd" })).toBeVisible();
    expect(screen.getByText("09:14:05", { selector: "dd" })).toBeVisible();
    expect(screen.getByText("409", { selector: "dd" })).toBeVisible();
    expect(screen.getByText("POST /api/launch", { selector: "dd" })).toBeVisible();
  });

  it("omits status and call rows when there are none", async () => {
    setup(dialogOf("SESSION_EXPIRED", { status: null, call: "" }));
    await userEvent.click(screen.getByRole("button", { name: copy["s8.details"] }));
    expect(screen.queryByText(copy["s8.details.http"])).toBeNull();
    expect(screen.queryByText(copy["s8.details.call"])).toBeNull();
  });

  it("copies the details and confirms with a balloon", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    setup(dialogOf("UNKNOWN_APP", { status: 404 }));
    await userEvent.click(screen.getByRole("button", { name: copy["s8.details"] }));
    await userEvent.click(screen.getByRole("button", { name: copy["s8.details.copy"] }));
    const text = writeText.mock.calls[0][0] as string;
    expect(text).toContain("Feilkode: UNKNOWN_APP");
    expect(text).toContain("HTTP-status: 404");
    expect(useBalloonStore.getState().balloon?.title).toBe(copy["s8.details.copied"]);
    vi.unstubAllGlobals();
  });

  it("never renders the raw error text", () => {
    setup(dialogOf("NETWORK", { status: null, call: "" }));
    expect(screen.getByRole("alertdialog")).not.toHaveTextContent(/TypeError|Failed to fetch/);
  });

  it("closes with the Lukk button except for an expired session", async () => {
    const { onClose, unmount } = setup(dialogOf("NETWORK"));
    await userEvent.click(screen.getByRole("button", { name: copy["common.close"] }));
    expect(onClose).toHaveBeenCalledOnce();
    unmount();
    setup(dialogOf("SESSION_EXPIRED"));
    expect(screen.queryByRole("button", { name: copy["common.close"] })).toBeNull();
  });

  it("NO_ACTIVE_PATIENT opens the patient list", async () => {
    const { onClose } = setup(dialogOf("NO_ACTIVE_PATIENT", { clientId: null }));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("NO_ACTIVE_PATIENT") }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith({ to: "/patients" });
  });

  it("NO_ACTIVE_ENCOUNTER starts a konsultasjon, hints with a balloon and opens the journal", async () => {
    const start = vi.fn().mockResolvedValue(undefined);
    useJournalStore.setState({ start });
    useWorkspaceStore.getState().openTab({ kind: "journal", label: "Journal" });
    useWorkspaceStore.getState().setCurrent("start");
    setup(dialogOf("NO_ACTIVE_ENCOUNTER"));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("NO_ACTIVE_ENCOUNTER") }));
    expect(start).toHaveBeenCalledOnce();
    expect(useBalloonStore.getState().balloon?.body).toBe(
      copy["s8.NO_ACTIVE_ENCOUNTER.balloon"]("Sykmelding"),
    );
    expect(useWorkspaceStore.getState().current).toBe("journal");
    expect(navigate).toHaveBeenCalledWith({
      to: "/patients/$patientId",
      params: { patientId: "p1" },
    });
  });

  it("PATIENT_MISMATCH makes the open journal the active patient again", async () => {
    const fetch = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ patientId: "p1", expiresAt: "2026-09-30T17:14:00Z" }),
    }));
    vi.stubGlobal("fetch", fetch);
    const { onClose } = setup(dialogOf("PATIENT_MISMATCH"));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("PATIENT_MISMATCH") }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(fetch).toHaveBeenCalledWith(
      "/api/active-patient",
      expect.objectContaining({ method: "PUT", body: JSON.stringify({ patientId: "p1" }) }),
    );
    vi.unstubAllGlobals();
  });

  it("UNKNOWN_APP reloads the apps and shows the Apper sub-tab", async () => {
    const load = vi.fn().mockResolvedValue(undefined);
    const { useAppsStore } = await import("./appsStore");
    useAppsStore.setState({ load });
    setup(dialogOf("UNKNOWN_APP"));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("UNKNOWN_APP") }));
    expect(load).toHaveBeenCalledOnce();
    expect(useJournalStore.getState().subTab).toBe("apper");
    expect(navigate).toHaveBeenCalled();
  });

  it("FRAMING_REFUSED opens the app in a browser tab", async () => {
    const open = vi.fn().mockReturnValue({});
    vi.stubGlobal("open", open);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ launchUrl: "https://syk.example/fhir/?launch=x" }),
      })),
    );
    seedRun();
    setup(dialogOf("FRAMING_REFUSED"));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("FRAMING_REFUSED") }));
    await vi.waitFor(() => expect(open).toHaveBeenCalledOnce());
    expect(open.mock.calls[0][2]).toBe("noopener,noreferrer");
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useAppRunStore.getState().tabApps.map((a) => a.clientId)).toEqual([sykInn.clientId]);
    vi.unstubAllGlobals();
  });

  it("SESSION_EXPIRED reloads the page", async () => {
    const reload = vi.fn();
    vi.stubGlobal("location", { ...window.location, reload });
    setup(dialogOf("SESSION_EXPIRED", { status: 401 }));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("SESSION_EXPIRED") }));
    expect(reload).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });

  it("NETWORK runs the retry callback", async () => {
    const retry = vi.fn();
    setup(dialogOf("NETWORK", { retry }));
    await userEvent.click(screen.getByRole("button", { name: primaryLabel("NETWORK") }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations", async () => {
    setup(dialogOf("NO_ACTIVE_ENCOUNTER"));
    await userEvent.click(screen.getByRole("button", { name: copy["s8.details"] }));
    await expectNoSeriousViolations(document.body);
  });
});
