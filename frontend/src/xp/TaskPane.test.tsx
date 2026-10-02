import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TaskPane } from "./TaskPane";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { useWorkspaceStore } from "./workspaceStore";

function renderPane(props: Partial<Parameters<typeof TaskPane>[0]> = {}) {
  const handlers = { onOpenPatients: vi.fn(), onLogout: vi.fn() };
  return {
    ...handlers,
    ...render(<TaskPane patientsCurrent={false} {...handlers} {...props} />),
  };
}

describe("TaskPane", () => {
  it("is a navigation landmark labelled Oppgaver", () => {
    renderPane();
    expect(
      screen.getByRole("navigation", { name: copy["nav.label"] }),
    ).toBeInTheDocument();
  });

  it("shows the four panels", () => {
    renderPane();
    const titles = screen
      .getAllByRole("heading", { level: 2 })
      .map((h) => h.textContent);
    expect(titles).toEqual([
      copy["pane.patient.title"],
      copy["pane.apps.title"],
      copy["pane.system.title"],
      copy["pane.soon.title"],
    ]);
  });

  it("shows that no patient is selected and offers to find one", async () => {
    const { onOpenPatients } = renderPane();
    const panel = screen.getByRole("region", {
      name: copy["pane.patient.title"],
    });
    expect(
      within(panel).getByText(copy["pane.patient.none"]),
    ).toBeInTheDocument();
    await userEvent.click(
      within(panel).getByRole("button", { name: copy["pane.patient.find"] }),
    );
    expect(onOpenPatients).toHaveBeenCalledOnce();
  });

  it("explains why apps are unavailable", () => {
    renderPane();
    const panel = screen.getByRole("region", { name: copy["pane.apps.title"] });
    expect(
      within(panel).getByText(copy["pane.apps.disabledReason"]),
    ).toBeInTheDocument();
  });

  it("marks Pasienter as current on the patients route", () => {
    renderPane({ patientsCurrent: true });
    expect(
      screen.getByRole("button", { name: copy["pane.system.patients"] }),
    ).toHaveAttribute("aria-current", "page");
  });

  it.each([
    ["kontrollpanel", "pane.system.kontroll"],
    ["sysinfo", "pane.system.sysinfo"],
    ["hjelp", "pane.system.hjelp"],
  ] as const)("opens %s as a document tab", async (kind, label) => {
    renderPane();
    const link = screen.getByRole("button", { name: copy[label] });
    expect(link).not.toHaveAttribute("aria-disabled");
    await userEvent.click(link);
    const { tabs, current } = useWorkspaceStore.getState();
    expect(current).toBe(kind);
    expect(tabs.map((t) => t.kind)).toEqual(["start", kind]);
    expect(tabs[1].label).toBe(copy[label]);
    expect(tabs[1].closable).toBe(true);
    expect(link).toHaveAttribute("aria-current", "page");
  });

  it("reuses an open system tab instead of opening a second one", async () => {
    renderPane();
    const link = screen.getByRole("button", {
      name: copy["pane.system.hjelp"],
    });
    await userEvent.click(link);
    await userEvent.click(
      screen.getByRole("button", { name: copy["pane.system.sysinfo"] }),
    );
    await userEvent.click(link);
    const { tabs, current } = useWorkspaceStore.getState();
    expect(tabs.map((t) => t.kind)).toEqual(["start", "hjelp", "sysinfo"]);
    expect(current).toBe("hjelp");
  });

  it("disables Hendelseslogg with the Fase 2 badge", () => {
    renderPane();
    const link = screen.getByRole("button", {
      name: new RegExp(copy["pane.system.logg"]),
    });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(
      document.getElementById(link.getAttribute("aria-describedby")!),
    ).toHaveTextContent(copy["badge.phase2"]);
  });

  it("disables all Kommer links", () => {
    renderPane();
    const panel = screen.getByRole("region", { name: copy["pane.soon.title"] });
    const links = within(panel).getAllByRole("button");
    expect(links).toHaveLength(3);
    links.forEach((link) =>
      expect(link).toHaveAttribute("aria-disabled", "true"),
    );
  });

  it("offers logout with the user name for narrow mode", async () => {
    const { onLogout } = renderPane({ userName: "Ola Nordmann" });
    await userEvent.click(
      screen.getByRole("button", {
        name: copy["pane.system.logoutNarrow"]("Ola Nordmann"),
      }),
    );
    expect(onLogout).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPane({ userName: "Ola Nordmann" });
    await expectNoSeriousViolations(container);
  });

  it("shows the active patient with journal and find-other links", async () => {
    useJournalStore.setState({
      patientId: "p1",
      patient: {
        id: "p1",
        fornavn: "Matematisk",
        etternavn: "Ape",
        personident: "01019012345",
        personidentType: "FNR" as const,
        birthDate: "1990-01-01",
        gender: "MALE" as const,
      },
      status: "ready",
    });
    const onOpenJournal = vi.fn();
    const { onOpenPatients, container } = renderPane({ onOpenJournal });
    expect(screen.getByText("Matematisk Ape")).toBeInTheDocument();
    expect(
      screen.queryByText(copy["pane.patient.none"]),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: copy["pane.patient.openJournal"] }),
    );
    expect(onOpenJournal).toHaveBeenCalledOnce();
    await userEvent.click(
      screen.getByRole("button", { name: copy["pane.patient.findOther"] }),
    );
    expect(onOpenPatients).toHaveBeenCalledOnce();
    await expectNoSeriousViolations(container);
    useJournalStore.getState().clear();
  });
});
