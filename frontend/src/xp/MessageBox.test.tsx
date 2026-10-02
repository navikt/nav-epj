import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MessageBox } from "./MessageBox";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

const details = {
  items: [
    ["Status", "502"],
    ["Tidspunkt", "12:00"],
  ] as const,
  onCopy: vi.fn(),
};

function renderBox(props: Partial<Parameters<typeof MessageBox>[0]> = {}) {
  const onClose = vi.fn();
  const onOk = vi.fn();
  const onCancel = vi.fn();
  render(
    <MessageBox
      title="nav-epj"
      heading="Kunne ikke lagre"
      variant="feil"
      buttons={[
        { label: "Avbryt", onClick: onCancel },
        { label: "Prøv igjen", onClick: onOk, isDefault: true },
      ]}
      onClose={onClose}
      {...props}
    >
      <p>Noe gikk galt.</p>
    </MessageBox>,
  );
  return { onClose, onOk, onCancel };
}

describe("MessageBox", () => {
  it.each(["feil", "advarsel", "sporsmal"] as const)(
    "uses alertdialog for %s",
    (variant) => {
      renderBox({ variant });
      expect(
        screen.getByRole("alertdialog", { name: "Kunne ikke lagre" }),
      ).toHaveAccessibleDescription("Noe gikk galt.");
    },
  );

  it("uses dialog for info", () => {
    renderBox({ variant: "info" });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("focuses the default button and activates buttons", async () => {
    const user = userEvent.setup();
    const { onOk, onCancel } = renderBox();
    expect(screen.getByRole("button", { name: "Prøv igjen" })).toHaveFocus();
    await user.click(screen.getByRole("button", { name: "Avbryt" }));
    expect(onCancel).toHaveBeenCalledOnce();
    await user.keyboard("{Enter}");
    await user.click(screen.getByRole("button", { name: "Prøv igjen" }));
    expect(onOk).toHaveBeenCalled();
  });

  it("closes on Escape", async () => {
    const user = userEvent.setup();
    const { onClose } = renderBox();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("omits technical details unless provided", () => {
    renderBox();
    expect(screen.queryByText(copy["s8.details"])).not.toBeInTheDocument();
  });

  it("expands and collapses technical details and copies them", async () => {
    const user = userEvent.setup();
    const onCopy = vi.fn();
    renderBox({ details: { items: details.items, onCopy } });
    const toggle = screen.getByRole("button", { name: copy["s8.details"] });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("502")).not.toBeVisible();
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("502")).toBeVisible();
    expect(screen.getByText("Status")).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: copy["s8.details.copy"] }),
    );
    expect(onCopy).toHaveBeenCalledOnce();
    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("has no serious accessibility violations", async () => {
    const user = userEvent.setup();
    renderBox({ details: { items: details.items, onCopy: vi.fn() } });
    await user.click(screen.getByRole("button", { name: copy["s8.details"] }));
    await expectNoSeriousViolations(document.body);
  });
});
