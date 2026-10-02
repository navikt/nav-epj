import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TaskLink } from "./TaskLink";
import { ShellContext } from "./shellContext";
import { expectNoSeriousViolations } from "./axeHelper";

describe("TaskLink", () => {
  it("renders a button with icon, label and sub-line", () => {
    render(<TaskLink icon="app" label="Sykmelding" sub="▣ Vindu" />);
    const button = screen.getByRole("button", { name: /Sykmelding/ });
    expect(button).toHaveClass("xp-tp-link");
    expect(button).toHaveTextContent("▣ Vindu");
    expect(button.querySelector("img")).toHaveAttribute("aria-hidden", "true");
  });

  it("activates on click and Enter, then closes the drawer", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    const setDrawerOpen = vi.fn();
    render(
      <ShellContext
        value={{
          narrow: true,
          drawerOpen: true,
          setDrawerOpen,
          announce: vi.fn(),
          rootElement: null,
        }}
      >
        <TaskLink icon="pasienter" label="Pasienter" onActivate={onActivate} />
      </ShellContext>,
    );
    await user.click(screen.getByRole("button"));
    await user.keyboard("{Enter}");
    expect(onActivate).toHaveBeenCalledTimes(2);
    expect(setDrawerOpen).toHaveBeenCalledWith(false);
  });

  it("marks the current link with aria-current", () => {
    render(<TaskLink icon="pasienter" label="Pasienter" current />);
    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-current", "page");
    expect(button).toHaveClass("is-current");
  });

  it("stays focusable when disabled and points at the visible badge", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    render(
      <TaskLink
        icon="hendelseslogg"
        label="Hendelseslogg"
        badge="Fase 2"
        disabled
        onActivate={onActivate}
      />,
    );
    const button = screen.getByRole("button", { name: /Hendelseslogg/ });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(button).not.toBeDisabled();
    const describedBy = button.getAttribute("aria-describedby")!;
    expect(document.getElementById(describedBy)).toHaveTextContent("Fase 2");
    await user.tab();
    expect(button).toHaveFocus();
    await user.keyboard("{Enter}");
    await user.click(button);
    expect(onActivate).not.toHaveBeenCalled();
  });

  it("prefers a visible reason over the badge for aria-describedby", () => {
    render(
      <TaskLink
        icon="app"
        label="Sykmelding"
        badge="Kommer"
        reason="Start en konsultasjon først."
        disabled
      />,
    );
    const button = screen.getByRole("button", { name: /Sykmelding/ });
    const reason = document.getElementById(
      button.getAttribute("aria-describedby")!,
    )!;
    expect(reason).toHaveTextContent("Start en konsultasjon først.");
    expect(reason).toBeVisible();
  });

  it("supports the stale state", () => {
    render(<TaskLink icon="app" label="Sykmelding" stale />);
    expect(screen.getByRole("button")).toHaveClass("is-stale");
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(
      <>
        <TaskLink icon="app" label="Sykmelding" sub="▣ Vindu" />
        <TaskLink icon="hjelp" label="Hjelp" badge="Fase 2" disabled />
        <TaskLink icon="app" label="Valider" reason="Ikke tilgjengelig." disabled />
      </>,
    );
    await expectNoSeriousViolations(container);
  });
});
