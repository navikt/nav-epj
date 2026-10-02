import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
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

describe("global shortcuts while a modal dialog is open", () => {
  it("ignores Ctrl+Shift+P", async () => {
    const user = await openDialog();
    await user.keyboard("{Control>}{Shift>}p{/Shift}{/Control}");
    expect(
      screen.getByLabelText(copy["header.search.label"]),
    ).not.toHaveFocus();
    expect(screen.getByRole("button", { name: "OK" })).toHaveFocus();
  });

  it("ignores F6", async () => {
    const user = await openDialog();
    await user.keyboard("{F6}");
    expect(screen.getByRole("button", { name: "OK" })).toHaveFocus();
  });

  it("ignores Ctrl+W and tab switching", async () => {
    const store = useWorkspaceStore.getState();
    store.openTab({ kind: "patients", label: "Pasienter" });
    const user = await openDialog();
    await user.keyboard("{Control>}w{/Control}");
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual([
      "start",
      "patients",
    ]);
    await user.keyboard("{Control>}{Alt>}{PageUp}{/Alt}{/Control}");
    expect(useWorkspaceStore.getState().current).toBe("patients");
  });

  it("restores the shortcuts once the dialog is closed", async () => {
    const user = await openDialog();
    await user.keyboard("{Escape}");
    act(() => {});
    await user.keyboard("{Control>}{Shift>}p{/Shift}{/Control}");
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveFocus();
  });

  it("keeps the shortcuts working without any dialog", async () => {
    const onSubmit = vi.fn();
    render(
      <AppShell>
        <AppHeader user={null} onLogout={() => {}} onSearchSubmit={onSubmit} />
      </AppShell>,
    );
    await userEvent.keyboard("{Control>}{Shift>}p{/Shift}{/Control}");
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveFocus();
  });
});
