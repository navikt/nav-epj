import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { AppHeader } from "./AppHeader";
import { AppShell } from "./AppShell";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { Workspace } from "./Workspace";
import { copy } from "./copy";
import { useWorkspaceStore } from "./workspaceStore";

function App() {
  const [open, setOpen] = useState(false);
  return (
    <AppShell>
      <AppHeader user={null} onLogout={() => {}} onSearchSubmit={() => {}} />
      <Workspace>
        <button type="button" onClick={() => setOpen(true)}>
          Åpne dialog
        </button>
      </Workspace>
      {open && (
        <Dialog title="Bekreft" onClose={() => setOpen(false)}>
          <Button isDefault>OK</Button>
        </Dialog>
      )}
    </AppShell>
  );
}

async function openDialog() {
  const user = userEvent.setup();
  render(<App />);
  await user.click(screen.getByRole("button", { name: "Åpne dialog" }));
  return user;
}

function press(init: KeyboardEventInit) {
  const event = new KeyboardEvent("keydown", {
    bubbles: true,
    cancelable: true,
    ...init,
  });
  document.dispatchEvent(event);
  return event;
}

const CHORDS: KeyboardEventInit[] = [
  { key: "w", code: "KeyW", ctrlKey: true },
  { key: "w", code: "KeyW", metaKey: true },
  { key: "w", code: "KeyW", ctrlKey: true, altKey: true },
  { key: "PageDown", ctrlKey: true, altKey: true },
  { key: "PageUp", metaKey: true, altKey: true },
  { key: "p", ctrlKey: true, shiftKey: true },
  { key: "p", metaKey: true, shiftKey: true },
  { key: "F6" },
  { key: "F6", shiftKey: true },
];

function openTabs() {
  act(() => {
    const store = useWorkspaceStore.getState();
    store.openTab({ kind: "patients", label: "Pasienter" });
    store.openTab({ kind: "hjelp", label: "Hjelp" });
  });
  return useWorkspaceStore.getState().tabs.map((t) => t.id);
}

describe("application shortcuts are not registered", () => {
  it("leaves every former shortcut to the browser and keeps the workspace", () => {
    render(<App />);
    const ids = openTabs();
    const current = useWorkspaceStore.getState().current;
    for (const chord of CHORDS) {
      expect(press(chord).defaultPrevented).toBe(false);
    }
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(ids);
    expect(useWorkspaceStore.getState().current).toBe(current);
    expect(screen.getByLabelText(copy["header.search.label"])).not.toHaveFocus();
  });

  it("leaves them alone while a dialog is open and keeps its focus", async () => {
    const user = await openDialog();
    const ids = openTabs();
    for (const chord of CHORDS) {
      expect(press(chord).defaultPrevented).toBe(false);
    }
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(ids);
    expect(screen.getByRole("button", { name: "OK" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
