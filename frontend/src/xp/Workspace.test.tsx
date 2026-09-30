import { act, render, screen } from "@testing-library/react";
import { useAppRunStore } from "./appRunStore";
import { seedApps, seedJournal, seedRun } from "./appFixtures";
import { beforeEach, describe, expect, it } from "vitest";
import { Workspace } from "./Workspace";
import { useWorkspaceStore } from "./workspaceStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

describe("Workspace", () => {
  beforeEach(() => act(() => useWorkspaceStore.getState().reset()));

  it("renders a main landmark with tabs and a tabpanel for the page", () => {
    render(
      <Workspace>
        <p>Sideinnhold</p>
      </Workspace>,
    );
    const main = screen.getByRole("main");
    expect(main).toContainElement(screen.getByRole("tablist"));
    const panel = screen.getByRole("tabpanel", { name: copy["tabs.start"] });
    expect(panel).toHaveAttribute("id", "work-panel");
    expect(panel).toHaveTextContent("Sideinnhold");
  });

  it("labels the panel by the current tab", () => {
    render(
      <Workspace>
        <p>Sideinnhold</p>
      </Workspace>,
    );
    act(() => {
      useWorkspaceStore.getState().openTab({ kind: "patients", label: "Pasienter" });
    });
    expect(screen.getByRole("tabpanel", { name: "Pasienter" })).toBeInTheDocument();
  });

  describe("app tabs", () => {
    beforeEach(() => {
      seedApps();
      seedJournal();
      seedRun();
      useAppRunStore.getState().setLaunchUrl("syk-inn", "https://syk.example/?launch=1");
      act(() => {
        useWorkspaceStore
          .getState()
          .openTab({ kind: "app", clientId: "syk-inn", label: "Sykmelding · ON" });
      });
    });

    it("hides the routed page and shows the app view while an app tab is current", () => {
      render(
        <Workspace>
          <p>Sideinnhold</p>
        </Workspace>,
      );
      expect(screen.getByText("Sideinnhold")).not.toBeVisible();
      expect(screen.getByRole("tabpanel")).toHaveClass("xp-page-app");
      expect(screen.getByTitle("Sykmelding (syk-inn) for Ola Nordmann")).toBeVisible();
    });

    it("keeps the app mounted but hidden when another tab is current", () => {
      render(
        <Workspace>
          <p>Sideinnhold</p>
        </Workspace>,
      );
      const frame = screen.getByTitle("Sykmelding (syk-inn) for Ola Nordmann");
      act(() => useWorkspaceStore.getState().setCurrent("start"));
      expect(screen.getByText("Sideinnhold")).toBeVisible();
      expect(screen.getByTitle("Sykmelding (syk-inn) for Ola Nordmann")).toBe(frame);
      expect(frame).not.toBeVisible();
      expect(screen.getByRole("tabpanel")).not.toHaveClass("xp-page-app");
    });
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(
      <Workspace>
        <h1>Tittel</h1>
      </Workspace>,
    );
    await expectNoSeriousViolations(container);
  });
});
