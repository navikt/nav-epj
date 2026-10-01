import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DocumentTabs } from "./DocumentTabs";
import { Workspace } from "./Workspace";
import { useWorkspaceStore } from "./workspaceStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

const store = () => useWorkspaceStore.getState();

function openSecondAndThird() {
  act(() => {
    store().openTab({ kind: "patients", label: "Pasienter" });
    store().openTab({ kind: "journal", label: "Journal · Ola Nordmann" });
  });
}

const tab = (name: string | RegExp) => screen.getByRole("tab", { name });
const closeButton = (label: string) =>
  document.querySelector<HTMLButtonElement>(
    `button[aria-label="${copy["tabs.close"](label)}"]`,
  )!;

describe("DocumentTabs", () => {
  beforeEach(() => act(() => store().reset()));

  it("renders a labelled tablist with only the pinned Start tab", () => {
    render(<DocumentTabs />);
    const list = screen.getByRole("tablist", { name: copy["tabs.label"] });
    const tabs = within(list).getAllByRole("tab");
    expect(tabs).toHaveLength(1);
    expect(tabs[0]).toHaveAccessibleName(copy["tabs.start"]);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(tabs[0]).toHaveAttribute("aria-controls", "work-panel");
    expect(document.querySelector("button.x")).toBeNull();
  });

  it("renders further tabs with close buttons and marks the current one", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    expect(screen.getAllByRole("tab")).toHaveLength(3);
    expect(tab("Journal · Ola Nordmann")).toHaveAttribute("aria-selected", "true");
    expect(tab("Pasienter")).toHaveAttribute("aria-selected", "false");
    expect(tab("Journal · Ola Nordmann").closest(".xp-doctab")).toHaveClass("is-current");
    const close = closeButton("Pasienter");
    expect(close).toHaveAttribute("title", copy["tabs.closeTooltip"]);
  });

  it("keeps the close button out of the tab order and the accessibility tree", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    const close = closeButton("Pasienter");
    expect(close).toHaveAttribute("aria-hidden", "true");
    expect(close).toHaveAttribute("tabindex", "-1");
    expect(tab("Pasienter")).toHaveAttribute("aria-keyshortcuts", "Delete");
    expect(tab(copy["tabs.start"])).not.toHaveAttribute("aria-keyshortcuts");
  });

  it("uses roving tabindex", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    expect(tab("Journal · Ola Nordmann")).toHaveAttribute("tabindex", "0");
    expect(tab("Pasienter")).toHaveAttribute("tabindex", "-1");
    expect(tab(copy["tabs.start"])).toHaveAttribute("tabindex", "-1");
  });

  it("moves between tabs and activates them with the arrow keys, Home and End", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    openSecondAndThird();
    tab("Journal · Ola Nordmann").focus();

    await user.keyboard("{ArrowRight}");
    expect(tab(copy["tabs.start"])).toHaveFocus();
    expect(store().current).toBe("start");

    await user.keyboard("{ArrowRight}");
    expect(tab("Pasienter")).toHaveFocus();
    expect(store().current).toBe("patients");

    await user.keyboard("{ArrowLeft}{ArrowLeft}");
    expect(tab("Journal · Ola Nordmann")).toHaveFocus();
    expect(store().current).toBe("journal");

    await user.keyboard("{Home}");
    expect(tab(copy["tabs.start"])).toHaveFocus();
    await user.keyboard("{End}");
    expect(tab("Journal · Ola Nordmann")).toHaveFocus();
  });

  it("activates a tab on click and reports it", async () => {
    const onActivate = vi.fn();
    render(<DocumentTabs onActivate={onActivate} />);
    openSecondAndThird();
    await userEvent.click(tab("Pasienter"));
    expect(store().current).toBe("patients");
    expect(onActivate).toHaveBeenCalledWith(expect.objectContaining({ id: "patients" }));
  });

  it("does not close the pinned Start tab with Ctrl+W but still swallows the shortcut", () => {
    render(<DocumentTabs />);
    const event = new KeyboardEvent("keydown", {
      key: "w",
      code: "KeyW",
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
    });
    act(() => {
      document.dispatchEvent(event);
    });
    expect(event.defaultPrevented).toBe(true);
    expect(store().tabs.map((t) => t.id)).toEqual(["start"]);
    expect(tab(copy["tabs.start"])).toBeInTheDocument();
  });

  it("registers the global keydown listener once across re-renders", () => {
    const add = vi.spyOn(document, "addEventListener");
    render(<DocumentTabs />);
    openSecondAndThird();
    act(() => store().setCurrent("patients"));
    const keydowns = add.mock.calls.filter(([type]) => type === "keydown");
    expect(keydowns).toHaveLength(1);
    fireEvent.keyDown(document, { key: "w", code: "KeyW", ctrlKey: true });
    expect(store().tabs.map((t) => t.id)).not.toContain("patients");
    add.mockRestore();
  });

  it("closes the current closable tab with Ctrl+W", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    openSecondAndThird();
    await user.keyboard("{Control>}w{/Control}");
    expect(store().tabs.map((t) => t.id)).toEqual(["start", "patients"]);
    expect(store().current).toBe("patients");
  });

  it("closes the current closable tab with the Ctrl+Alt+W fallback", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    openSecondAndThird();
    await user.keyboard("{Control>}{Alt>}w{/Alt}{/Control}");
    expect(store().tabs.map((t) => t.id)).toEqual(["start", "patients"]);
  });

  it("closes the current closable tab with Cmd+W (Mac)", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    fireEvent.keyDown(document, { key: "w", code: "KeyW", metaKey: true });
    expect(store().tabs.map((t) => t.id)).toEqual(["start", "patients"]);
  });

  it("switches tabs with Ctrl+Alt+PageDown and Ctrl+Alt+PageUp", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    openSecondAndThird();
    await user.keyboard("{Control>}{Alt>}{PageDown}{/Alt}{/Control}");
    expect(store().current).toBe("start");
    await user.keyboard("{Control>}{Alt>}{PageUp}{/Alt}{/Control}");
    expect(store().current).toBe("journal");
  });

  it("switches tabs with Cmd+Alt+PageDown (Mac)", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    fireEvent.keyDown(document, {
      key: "PageDown",
      metaKey: true,
      altKey: true,
    });
    expect(store().current).toBe("start");
  });

  it("closes with Delete and moves focus to the new current tab", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    openSecondAndThird();
    tab("Journal · Ola Nordmann").focus();
    await user.keyboard("{Delete}");
    expect(screen.queryByRole("tab", { name: /Journal/ })).not.toBeInTheDocument();
    expect(tab("Pasienter")).toHaveFocus();
  });

  it("ignores Delete on the pinned Start tab", async () => {
    const user = userEvent.setup();
    render(<DocumentTabs />);
    tab(copy["tabs.start"]).focus();
    await user.keyboard("{Delete}");
    expect(store().tabs).toHaveLength(1);
  });

  it("closes a tab from its close button", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    render(<DocumentTabs onActivate={onActivate} />);
    openSecondAndThird();
    await user.click(
      closeButton("Journal · Ola Nordmann"),
    );
    expect(store().current).toBe("patients");
    expect(onActivate).toHaveBeenCalledWith(expect.objectContaining({ id: "patients" }));
    expect(tab("Pasienter")).toHaveFocus();
  });

  it("shows unsaved, status mark, error and full aria-label", () => {
    render(<DocumentTabs />);
    act(() => {
      store().openTab({
        kind: "app",
        clientId: "syk-inn",
        label: "Sykmelding · MA",
        mark: "●",
        ariaLabel: "Sykmelding for MATEMATISK APE, kjører",
        unsaved: true,
        error: true,
      });
    });
    const appTab = tab("Sykmelding for MATEMATISK APE, kjører");
    expect(appTab).toHaveTextContent("Sykmelding · MA •");
    expect(appTab).toHaveTextContent("●");
    expect(appTab.closest(".xp-doctab")).toHaveClass("is-error");
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(
      <Workspace>
        <p>Innhold</p>
      </Workspace>,
    );
    openSecondAndThird();
    await expectNoSeriousViolations(container);
  });

  it("does not react to unrelated shortcuts", () => {
    render(<DocumentTabs />);
    openSecondAndThird();
    fireEvent.keyDown(document, { key: "q", ctrlKey: true });
    expect(store().tabs).toHaveLength(3);
  });

  it("keeps a tab open when onBeforeClose vetoes every close path", async () => {
    const onBeforeClose = vi.fn(() => false);
    render(<DocumentTabs onBeforeClose={onBeforeClose} />);
    openSecondAndThird();
    fireEvent.keyDown(document, { key: "w", code: "KeyW", ctrlKey: true });
    await userEvent.click(closeButton("Journal · Ola Nordmann"));
    tab("Journal · Ola Nordmann").focus();
    await userEvent.keyboard("{Delete}");
    expect(onBeforeClose).toHaveBeenCalledTimes(3);
    expect(store().tabs).toHaveLength(3);
  });

  it("closes the tab when onBeforeClose allows it", async () => {
    const onBeforeClose = vi.fn(() => true);
    render(<DocumentTabs onBeforeClose={onBeforeClose} />);
    openSecondAndThird();
    await userEvent.click(closeButton("Journal · Ola Nordmann"));
    expect(onBeforeClose).toHaveBeenCalledWith(
      expect.objectContaining({ id: "journal" }),
    );
    expect(store().tabs.map((t) => t.id)).toEqual(["start", "patients"]);
  });
});
