import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AppToolbar } from "./AppToolbar";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function setup(props: Partial<Parameters<typeof AppToolbar>[0]> = {}) {
  const handlers = {
    onBack: vi.fn(),
    onForward: vi.fn(),
    onReload: vi.fn(),
    onToggleDev: vi.fn(),
    onPopOut: vi.fn(),
    onClose: vi.fn(),
  };
  const view = render(
    <>
      <div id="app-frame" />
      <div id="dev-panel" />
      <AppToolbar
        app="Sykmelding"
        icon="sykmelding"
        title="Sykmelding — Ola Nordmann — Konsultasjon 30.09 09:14"
        canNavigate
        canReload
        devOpen={false}
        {...handlers}
        {...props}
      />
    </>,
  );
  return { ...view, ...handlers };
}

describe("AppToolbar", () => {
  it("is a labelled toolbar controlling the frame", () => {
    setup();
    const bar = screen.getByRole("toolbar", { name: copy["s5.toolbar.label"] });
    expect(bar).toHaveAttribute("aria-controls", "app-frame");
    expect(
      screen.getByRole("heading", { level: 1, name: /Ola Nordmann/ }),
    ).toBeInTheDocument();
  });

  it("names the navigation buttons after the app", async () => {
    const { onBack, onForward, onReload } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Tilbake i Sykmelding" }));
    await userEvent.click(screen.getByRole("button", { name: "Frem i Sykmelding" }));
    await userEvent.click(
      screen.getByRole("button", { name: copy["s5.reload"]("Sykmelding") }),
    );
    expect(onBack).toHaveBeenCalledOnce();
    expect(onForward).toHaveBeenCalledOnce();
    expect(onReload).toHaveBeenCalledOnce();
  });

  it("disables navigation and reload when told to", () => {
    setup({ canNavigate: false, canReload: false });
    expect(screen.getByRole("button", { name: "Tilbake i Sykmelding" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Frem i Sykmelding" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: copy["s5.reload"]("Sykmelding") }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: copy["s5.popout"] })).toBeDisabled();
  });

  it("exposes the developer tools toggle state", async () => {
    const { onToggleDev } = setup({ devOpen: true });
    const toggle = screen.getByRole("button", { name: copy["s5.dev"] });
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    expect(toggle).toHaveAttribute("aria-controls", "dev-panel");
    await userEvent.click(toggle);
    expect(onToggleDev).toHaveBeenCalledOnce();
  });

  it("moves between buttons with the arrow keys and skips disabled ones", async () => {
    setup({ canNavigate: false });
    const reload = screen.getByRole("button", {
      name: copy["s5.reload"]("Sykmelding"),
    });
    reload.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: copy["s5.dev"] })).toHaveFocus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(reload).toHaveFocus();
    await userEvent.keyboard("{ArrowLeft}");
    expect(screen.getByRole("button", { name: copy["s5.close"] })).toHaveFocus();
  });

  it("pops out and closes", async () => {
    const { onPopOut, onClose } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s5.popout"] }));
    await userEvent.click(screen.getByRole("button", { name: copy["s5.close"] }));
    expect(onPopOut).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = setup();
    await expectNoSeriousViolations(container);
  });
});
