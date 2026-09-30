import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SmartAppsPanel } from "./SmartAppsPanel";
import { kari, nyFane, ola, seedApps, seedJournal, seedRun, sykInn, validator } from "./appFixtures";
import { useActivePatientStore } from "./activePatientStore";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useWorkspaceStore } from "./workspaceStore";

describe("SmartAppsPanel", () => {
  beforeEach(() => {
    seedApps([sykInn, validator, nyFane]);
    seedJournal();
  });

  it("is a panel titled SMART-apper with one link per app and its mode", () => {
    render(<SmartAppsPanel />);
    expect(screen.getByText(copy["pane.apps.title"])).toBeInTheDocument();
    for (const name of ["Sykmelding", "Validator", "Fanen"]) {
      expect(screen.getByRole("button", { name: new RegExp(name) })).toBeInTheDocument();
    }
    expect(screen.getByText(copy["pane.apps.mode.ask"])).toBeInTheDocument();
  });

  it("starts the clicked app", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ launchUrl: "https://syk.example/?launch=1" }),
      })),
    );
    render(<SmartAppsPanel />);
    await userEvent.click(screen.getByRole("button", { name: /Sykmelding/ }));
    await vi.waitFor(() => expect(useAppRunStore.getState().runs).toHaveLength(1));
    vi.unstubAllGlobals();
  });

  it("disables apps without a konsultasjon and points at the reason", () => {
    seedJournal(ola, []);
    render(<SmartAppsPanel />);
    const link = screen.getByRole("button", { name: /Sykmelding/ });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(link).toHaveAccessibleDescription(copy["pane.apps.disabledReason"]);
  });

  it("keeps a running app reachable and marks it", () => {
    seedJournal(ola, []);
    seedRun();
    render(<SmartAppsPanel />);
    const link = screen.getByRole("button", { name: /Sykmelding/ });
    expect(link).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText(copy["pane.apps.runningHost"])).toBeInTheDocument();
  });

  it("flags a running app as utdatert when another window changed the active patient", () => {
    seedRun();
    useActivePatientStore.setState({ activeId: "p2" });
    render(<SmartAppsPanel />);
    expect(screen.getByText(copy["context.stale"])).toBeInTheDocument();
    expect(screen.queryByText(copy["pane.apps.runningHost"])).toBeNull();
  });

  it("marks the app tab that is current", () => {
    seedRun();
    useWorkspaceStore.getState().openTab({ kind: "app", clientId: "syk-inn", label: "Sykmelding" });
    render(<SmartAppsPanel />);
    expect(screen.getByRole("button", { name: /Sykmelding/ })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("lists tab apps and opens their dialog, flagged when stale", async () => {
    useAppRunStore.getState().addTabApp({
      id: "smart-ny-fane-1",
      clientId: "ny-fane",
      navn: "Fanen",
      patient: ola,
      startedAt: new Date(),
    });
    const { unmount } = render(<SmartAppsPanel />);
    expect(screen.getByText(copy["pane.apps.runningTab"])).toBeInTheDocument();
    unmount();
    seedJournal(kari);
    render(<SmartAppsPanel />);
    expect(screen.getByText(copy["pane.apps.stale"])).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: new RegExp(copy["pane.apps.stale"]) }));
    expect(useAppDialogStore.getState().dialog).toEqual({ kind: "tabApp", tabId: "smart-ny-fane-1" });
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<SmartAppsPanel />);
    await expectNoSeriousViolations(container);
  });
});
