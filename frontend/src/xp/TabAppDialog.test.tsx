import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TabAppDialog } from "./TabAppDialog";
import { kari, nyFane, ola, seedApps, seedJournal } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function setup() {
  const onClose = vi.fn();
  const view = render(<TabAppDialog tabId="smart-ny-fane-1" onClose={onClose} />);
  return { ...view, onClose };
}

function seedTabApp() {
  useAppRunStore.getState().addTabApp({
    id: "smart-ny-fane-1",
    clientId: "ny-fane",
    navn: "Fanen",
    patient: ola,
    startedAt: new Date(2026, 8, 30, 9, 14),
  });
}

describe("TabAppDialog", () => {
  beforeEach(() => {
    seedApps([nyFane]);
    seedJournal();
    seedTabApp();
  });

  it("renders nothing for an unknown app", () => {
    useAppRunStore.getState().removeTabApp("smart-ny-fane-1");
    const { container } = setup();
    expect(container).toBeEmptyDOMElement();
  });

  it("informs about a running tab app with its access expiry", () => {
    setup();
    expect(
      screen.getByRole("dialog", {
        name: copy["s7.tabInfo.head"]("Fanen", "Ola Nordmann"),
      }),
    ).toHaveTextContent(copy["s7.tabInfo.title"]("Fanen"));
    expect(screen.getByText(copy["s7.tabInfo.body"]("10:14"))).toBeInTheDocument();
  });

  it("removes the app from the list", async () => {
    const { onClose } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s7.tabInfo.remove"] }));
    expect(useAppRunStore.getState().tabApps).toEqual([]);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("restarts in a new tab with a fresh launch", async () => {
    vi.stubGlobal("open", vi.fn().mockReturnValue({}));
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ launchUrl: "https://f.example/?launch=2" }),
      })),
    );
    setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s7.tabInfo.restart"] }));
    await vi.waitFor(() => expect(window.open).toHaveBeenCalledOnce());
    expect(useAppRunStore.getState().tabApps).toHaveLength(1);
    expect(useAppRunStore.getState().tabApps[0].startedAt.getTime()).toBeGreaterThan(
      new Date(2026, 8, 30, 9, 14).getTime(),
    );
    vi.unstubAllGlobals();
  });

  it("warns that the tab belongs to the previous patient when the journal moved on", async () => {
    seedJournal(kari);
    const { onClose } = setup();
    const dialog = screen.getByRole("alertdialog", {
      name: copy["s7.staleDlg.head"]("Fanen", "Ola Nordmann"),
    });
    expect(dialog).toHaveTextContent(copy["s7.staleDlg.title"]("Fanen"));
    expect(screen.getByText(copy["s7.staleDlg.body1"]("10:14"))).toBeInTheDocument();
    expect(screen.getByText(copy["s7.staleDlg.body2"]("Kari Hansen"))).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: copy["s7.staleDlg.done"] }));
    expect(useAppRunStore.getState().tabApps).toEqual([]);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes without changes", async () => {
    const { onClose } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["common.close"] }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(useAppRunStore.getState().tabApps).toHaveLength(1);
  });

  it("has no serious accessibility violations in both variants", async () => {
    const { unmount } = setup();
    await expectNoSeriousViolations(document.body);
    unmount();
    seedJournal(kari);
    setup();
    await expectNoSeriousViolations(document.body);
  });
});
