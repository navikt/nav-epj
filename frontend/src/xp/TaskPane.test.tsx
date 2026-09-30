import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TaskPane } from "./TaskPane";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function renderPane(props: Partial<Parameters<typeof TaskPane>[0]> = {}) {
  const handlers = { onOpenPatients: vi.fn(), onLogout: vi.fn() };
  return {
    ...handlers,
    ...render(
      <TaskPane patientsCurrent={false} {...handlers} {...props} />,
    ),
  };
}

describe("TaskPane", () => {
  it("is a navigation landmark labelled Oppgaver", () => {
    renderPane();
    expect(
      screen.getByRole("navigation", { name: copy["nav.label"] }),
    ).toBeInTheDocument();
  });

  it("shows the four panels plus the temporary appearance panel", () => {
    renderPane();
    const titles = screen
      .getAllByRole("heading", { level: 2 })
      .map((h) => h.textContent);
    expect(titles).toEqual([
      copy["pane.patient.title"],
      copy["pane.apps.title"],
      copy["pane.system.title"],
      copy["pane.soon.title"],
      copy["s9.tema.theme"],
    ]);
  });

  it("shows that no patient is selected and offers to find one", async () => {
    const { onOpenPatients } = renderPane();
    const panel = screen.getByRole("region", { name: copy["pane.patient.title"] });
    expect(within(panel).getByText(copy["pane.patient.none"])).toBeInTheDocument();
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

  it("disables Hendelseslogg with the Fase 2 badge", () => {
    renderPane();
    const link = screen.getByRole("button", { name: new RegExp(copy["pane.system.logg"]) });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(document.getElementById(link.getAttribute("aria-describedby")!)).toHaveTextContent(
      copy["badge.phase2"],
    );
  });

  it("disables all Kommer links", () => {
    renderPane();
    const panel = screen.getByRole("region", { name: copy["pane.soon.title"] });
    const links = within(panel).getAllByRole("button");
    expect(links).toHaveLength(3);
    links.forEach((link) => expect(link).toHaveAttribute("aria-disabled", "true"));
  });

  it("offers logout with the user name for narrow mode", async () => {
    const { onLogout } = renderPane({ userName: "Ola Nordmann" });
    await userEvent.click(
      screen.getByRole("button", { name: copy["pane.system.logoutNarrow"]("Ola Nordmann") }),
    );
    expect(onLogout).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPane({ userName: "Ola Nordmann" });
    await expectNoSeriousViolations(container);
  });
});
