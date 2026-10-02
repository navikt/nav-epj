import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { Dialog } from "./Dialog";
import { Button } from "./Button";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function Harness({ onClose = () => {} }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Åpne
      </button>
      {open && (
        <Dialog
          title="Innstillinger"
          onClose={() => {
            onClose();
            setOpen(false);
          }}
        >
          <p>Innhold</p>
          <input aria-label="Felt" />
          <Button>Avbryt</Button>
          <Button isDefault>OK</Button>
        </Dialog>
      )}
    </>
  );
}

describe("Dialog", () => {
  it("renders a labelled modal dialog with only a close button in the title bar", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Åpne" }));
    const dialog = screen.getByRole("dialog", { name: "Innstillinger" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    const titleButtons = dialog.querySelectorAll(".xp-titlebar button");
    expect(titleButtons).toHaveLength(1);
    expect(titleButtons[0]).toHaveAccessibleName(copy["common.closeDialog"]);
  });

  it("supports the alertdialog role", () => {
    render(
      <Dialog title="Advarsel" role="alertdialog" onClose={() => {}}>
        <Button isDefault>OK</Button>
      </Dialog>,
    );
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  });

  it("focuses the default button on open", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Åpne" }));
    expect(screen.getByRole("button", { name: "OK" })).toHaveFocus();
  });

  it("prefers an element marked for autofocus over the default button", () => {
    render(
      <Dialog title="Skjema" onClose={() => {}}>
        <input aria-label="Felt" data-autofocus="" />
        <Button isDefault>OK</Button>
      </Dialog>,
    );
    expect(screen.getByLabelText("Felt")).toHaveFocus();
  });

  it("falls back to the first control in the body without a default button", () => {
    render(
      <Dialog title="Skjema" onClose={() => {}}>
        <input aria-label="Felt" />
      </Dialog>,
    );
    expect(screen.getByLabelText("Felt")).toHaveFocus();
  });

  it("traps focus with Tab and Shift+Tab", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Åpne" }));
    const dialog = screen.getByRole("dialog");
    const close = screen.getByRole("button", {
      name: copy["common.closeDialog"],
    });
    const ok = screen.getByRole("button", { name: "OK" });
    expect(ok).toHaveFocus();
    await user.tab();
    expect(close).toHaveFocus();
    await user.tab({ shift: true });
    expect(ok).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "Avbryt" })).toHaveFocus();
    expect(dialog).toContainElement(document.activeElement as HTMLElement);
  });

  it("closes on Escape and returns focus to the opener", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const opener = screen.getByRole("button", { name: "Åpne" });
    await user.click(opener);
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it("closes from the title bar button and returns focus", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const opener = screen.getByRole("button", { name: "Åpne" });
    await user.click(opener);
    await user.click(
      screen.getByRole("button", { name: copy["common.closeDialog"] }),
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it("lets an inner control handle Escape without closing the dialog", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Dialog title="Søk" onClose={onClose}>
        <input
          aria-label="Felt"
          onKeyDown={(event) => {
            if (event.key === "Escape") event.preventDefault();
          }}
        />
      </Dialog>,
    );
    await user.keyboard("{Escape}");
    expect(onClose).not.toHaveBeenCalled();
    screen.getByRole("dialog").focus();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("does not let Escape reach outer listeners", async () => {
    const user = userEvent.setup();
    const outer = vi.fn();
    document.addEventListener("keydown", outer);
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Åpne" }));
    await user.keyboard("{Escape}");
    document.removeEventListener("keydown", outer);
    expect(outer).not.toHaveBeenCalled();
  });

  it("has no serious accessibility violations", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Åpne" }));
    await expectNoSeriousViolations(document.body);
  });
});
