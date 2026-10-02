import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { kari, ola } from "./appFixtures";
import { useActivePatientStore } from "./activePatientStore";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { CurrentUserContext, type CurrentUser } from "./currentUser";
import { useJournalGuardStore } from "./journalGuardStore";
import { useJournalStore } from "./journalStore";
import { usePatientsStore } from "./patientsStore";
import { SEARCH_INPUT_ID } from "./shellContext";
import { StartTab } from "./StartTab";
import { useWorkspaceStore } from "./workspaceStore";

const navigate = vi.fn();

vi.mock("@tanstack/react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@tanstack/react-router")>()),
  useNavigate: () => navigate,
}));

const user: CurrentUser = {
  navn: "Kari Nordmann",
  hpr: "9144889",
  autorisasjon: "Lege",
  legekontor: "Storgata legekontor",
};

function renderPage(currentUser: CurrentUser | null = user) {
  return render(
    <CurrentUserContext.Provider value={currentUser}>
      <StartTab />
    </CurrentUserContext.Provider>,
  );
}

function card(name: string) {
  return within(screen.getByRole("region", { name }));
}

describe("StartTab", () => {
  beforeEach(() => {
    navigate.mockReset();
    usePatientsStore.getState().reset();
    useAppRunStore.setState({ runs: [], tabApps: [] });
    useJournalStore.setState({
      patientId: null,
      patient: null,
      konsultasjoner: [],
      status: "idle",
    });
    useActivePatientStore.setState({ activeId: null });
    useJournalGuardStore.getState().setInAppTarget(null);
    useWorkspaceStore.getState().reset();
    document.body.innerHTML = "";
  });

  it("greets the signed-in user", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s2.greeting"]("Kari Nordmann") }),
    ).toBeInTheDocument();
  });

  it("shows a loading state before the user is known", () => {
    renderPage(null);
    expect(screen.getByRole("status")).toHaveTextContent(copy["common.loading"]);
  });

  it("opens the Pasienter tab from the Finn pasient card", async () => {
    const userEv = userEvent.setup();
    renderPage();
    await userEv.click(
      card(copy["s2.find.title"]).getByRole("button", { name: copy["s2.find.open"] }),
    );
    expect(navigate).toHaveBeenCalledWith({ to: "/patients" });
  });

  it("focuses the header search field from the Finn pasient card", async () => {
    const input = document.createElement("input");
    input.id = SEARCH_INPUT_ID;
    document.body.appendChild(input);
    const userEv = userEvent.setup();
    renderPage();
    await userEv.click(
      card(copy["s2.find.title"]).getByRole("button", { name: copy["s2.find.search"] }),
    );
    expect(input).toHaveFocus();
  });

  it("hides the Nylig åpnet card when nothing has been opened yet", () => {
    renderPage();
    expect(screen.queryByText(copy["s2.recent.title"])).not.toBeInTheDocument();
  });

  it("lists recently opened patients with birth date and last konsultasjon", () => {
    usePatientsStore.setState({
      patients: [ola, kari],
      recentIds: [ola.id, kari.id],
      lastKonsultasjon: {
        [ola.id]: { status: "FULLFØRT", tidspunkt: "2026-09-20T10:00:00" },
      },
    });
    renderPage();
    const recent = card(copy["s2.recent.title"]);
    expect(recent.getByText("Ola Nordmann")).toBeInTheDocument();
    expect(
      recent.getByText(copy["s2.recent.item"]("01.01.1990", "20.09.2026")),
    ).toBeInTheDocument();
    expect(recent.getByText("Kari Hansen")).toBeInTheDocument();
    expect(recent.getByText(copy["s2.recent.item"]("01.01.1990", "–"))).toBeInTheDocument();
  });

  it("opens a recent patient's journal through the safe journal-open guard", async () => {
    usePatientsStore.setState({ patients: [ola], recentIds: [ola.id], lastKonsultasjon: {} });
    const userEv = userEvent.setup();
    renderPage();
    await userEv.click(card(copy["s2.recent.title"]).getByText("Ola Nordmann"));
    expect(useJournalGuardStore.getState().inAppTarget).toBe(ola.id);
    expect(navigate).toHaveBeenCalled();
  });

  it("hides the Apper i egne faner card when no tab app is running", () => {
    renderPage();
    expect(screen.queryByText(copy["s2.tabApps.title"])).not.toBeInTheDocument();
  });

  it("shows a running note for a tab app bound to the open journal", () => {
    useJournalStore.setState({ patientId: ola.id, patient: ola, konsultasjoner: [], status: "ready" });
    useAppRunStore.setState({
      tabApps: [
        { id: "t1", clientId: "syk-inn", navn: "Sykmelding", patient: ola, startedAt: new Date() },
      ],
    });
    renderPage();
    const apps = card(copy["s2.tabApps.title"]);
    expect(apps.getByText(/Kjører i egen fane/)).toBeInTheDocument();
  });

  it("shows a stale note for a tab app bound to a different patient than the open journal", () => {
    useJournalStore.setState({ patientId: kari.id, patient: kari, konsultasjoner: [], status: "ready" });
    useAppRunStore.setState({
      tabApps: [
        { id: "t1", clientId: "syk-inn", navn: "Sykmelding", patient: ola, startedAt: new Date() },
      ],
    });
    renderPage();
    const apps = card(copy["s2.tabApps.title"]);
    expect(apps.getByText(/Tilhører forrige pasient/)).toBeInTheDocument();
  });

  it("always shows the Om testmiljøet card", () => {
    renderPage();
    const about = card(copy["s2.about.title"]);
    expect(about.getByText(copy["s2.about.body"])).toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    usePatientsStore.setState({ patients: [ola], recentIds: [ola.id], lastKonsultasjon: {} });
    useAppRunStore.setState({
      tabApps: [
        { id: "t1", clientId: "syk-inn", navn: "Sykmelding", patient: ola, startedAt: new Date() },
      ],
    });
    const { container } = renderPage();
    await expectNoSeriousViolations(container);
  });
});
