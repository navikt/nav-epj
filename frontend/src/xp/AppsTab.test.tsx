import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppsTab } from "./AppsTab";
import { nyFane, ola, seedApps, seedJournal, seedRun, sykInn, validator } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";

describe("AppsTab", () => {
  beforeEach(() => {
    seedApps([sykInn, validator, nyFane]);
    seedJournal();
  });

  it("lists one card per registered app with description and launch mode", () => {
    render(<AppsTab />);
    const cards = screen.getAllByRole("region");
    expect(cards.map((c) => within(c).getByRole("heading").textContent)).toEqual([
      "Sykmelding",
      "Validator",
      "Fanen",
    ]);
    expect(within(cards[0]).getByText(sykInn.beskrivelse ?? "")).toBeInTheDocument();
    expect(within(cards[0]).getByText(copy["pane.apps.mode.iframe"])).toBeInTheDocument();
    expect(within(cards[1]).getByText(copy["pane.apps.mode.ask"])).toBeInTheDocument();
    expect(within(cards[2]).getByText(copy["pane.apps.mode.tab"])).toBeInTheDocument();
    expect(screen.getByText(copy["s4.apps.help"])).toBeInTheDocument();
  });

  it("starts an app when a konsultasjon is ongoing", async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ launchUrl: "https://syk.example/?launch=1" }),
    }));
    vi.stubGlobal("fetch", fetchMock);
    render(<AppsTab />);
    const [start] = screen.getAllByRole("button", { name: copy["s4.apps.start"] });
    expect(start).not.toHaveAttribute("aria-disabled", "true");
    await userEvent.click(start);
    await vi.waitFor(() => expect(useAppRunStore.getState().runs).toHaveLength(1));
    vi.unstubAllGlobals();
  });

  it("disables starting without a konsultasjon and explains why", async () => {
    seedJournal(ola, []);
    render(<AppsTab />);
    const starts = screen.getAllByRole("button", { name: copy["s4.apps.start"] });
    for (const start of starts) {
      expect(start).toHaveAttribute("aria-disabled", "true");
      expect(start).toHaveAccessibleDescription(
        `${copy["s4.apps.disabled.title"]} ${copy["s4.apps.disabled.body"]}`,
      );
    }
    await userEvent.click(starts[0]);
    expect(useAppRunStore.getState().runs).toEqual([]);
  });

  it("does not describe enabled buttons with the reason", () => {
    render(<AppsTab />);
    expect(screen.queryByText(copy["s4.apps.disabled.title"])).toBeNull();
    expect(
      screen.getAllByRole("button", { name: copy["s4.apps.start"] })[0],
    ).not.toHaveAttribute("aria-describedby");
  });

  it("shows running state per app", () => {
    seedRun();
    useAppRunStore.getState().addTabApp({
      id: "smart-ny-fane-1",
      clientId: "ny-fane",
      navn: "Fanen",
      patient: ola,
      startedAt: new Date(),
    });
    render(<AppsTab />);
    expect(screen.getByText(copy["s4.apps.runningHost"])).toBeInTheDocument();
    expect(screen.getByText(copy["s4.apps.runningTab"])).toBeInTheDocument();
  });

  it("shows a loading status while apps load", () => {
    useAppsStore.setState({ apps: [], status: "loading" });
    render(<AppsTab />);
    expect(screen.getByRole("status")).toHaveTextContent(copy["common.loading"]);
  });

  it("offers a retry when loading failed", async () => {
    const load = vi.fn().mockResolvedValue(undefined);
    useAppsStore.setState({ apps: [], status: "error", load });
    render(<AppsTab />);
    expect(screen.getByRole("alert")).toHaveTextContent(copy["s8.NETWORK.head"]);
    await userEvent.click(screen.getByRole("button", { name: copy["s8.NETWORK.action"] }));
    expect(load).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations enabled and disabled", async () => {
    const { container, rerender } = render(<AppsTab />);
    await expectNoSeriousViolations(container);
    useJournalStore.setState({ konsultasjoner: [] });
    rerender(<AppsTab />);
    await expectNoSeriousViolations(container);
  });
});
