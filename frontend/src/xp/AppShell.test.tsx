import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppShell } from "./AppShell";
import { useShell } from "./shellContext";
import { usePreferencesStore } from "./preferencesStore";
import { mockMatchMedia } from "./matchMediaMock";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function Landmarks() {
  const { drawerOpen, setDrawerOpen, announce, narrow } = useShell();
  return (
    <>
      <header data-xp-landmark="header" tabIndex={-1}>
        <button
          id="xp-menu-button"
          aria-expanded={drawerOpen}
          onClick={() => setDrawerOpen(!drawerOpen)}
        >
          {copy["header.menu"]}
        </button>
        <button onClick={() => announce("Lagret")}>Announce</button>
        <span>{narrow ? "narrow" : "wide"}</span>
      </header>
      <nav
        aria-label={copy["nav.label"]}
        data-xp-landmark="nav"
        tabIndex={-1}
        className="xp-taskpane"
      >
        <button>Pasienter</button>
      </nav>
      <main data-xp-landmark="main" tabIndex={-1}>
        <button>Innhold</button>
      </main>
      <footer aria-label={copy["status.label"]} data-xp-landmark="footer" tabIndex={-1}>
        Klar
      </footer>
    </>
  );
}

function renderShell() {
  return render(
    <AppShell>
      <Landmarks />
    </AppShell>,
  );
}

const landmark = (name: string) =>
  document.querySelector(`[data-xp-landmark="${name}"]`);

describe("AppShell", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    usePreferencesStore.setState({
      theme: "luna",
      reduceMotion: false,
      textScale: 100,
    });
  });

  it("renders the four landmarks", () => {
    renderShell();
    expect(screen.getByRole("banner")).toBeInTheDocument();
    expect(
      screen.getByRole("navigation", { name: copy["nav.label"] }),
    ).toBeInTheDocument();
    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(
      screen.getByRole("contentinfo", { name: copy["status.label"] }),
    ).toBeInTheDocument();
  });

  it("has exactly one polite live region that announces messages", async () => {
    const { container } = renderShell();
    const regions = container.querySelectorAll('[aria-live="polite"]');
    expect(regions).toHaveLength(1);
    await userEvent.click(screen.getByText("Announce"));
    expect(regions[0]).toHaveTextContent("Lagret");
  });

  it("re-announces an identical message", async () => {
    const { container } = renderShell();
    const region = container.querySelector('[aria-live="polite"]')!;
    await userEvent.click(screen.getByText("Announce"));
    const first = region.textContent;
    await userEvent.click(screen.getByText("Announce"));
    expect(region.textContent).not.toBe(first);
    expect(region.textContent?.trim()).toBe("Lagret");
  });

  it("does not move focus between landmarks with F6", async () => {
    const user = userEvent.setup();
    renderShell();
    await user.keyboard("{F6}");
    await user.keyboard("{Shift>}{F6}{/Shift}");
    expect(document.body).toHaveFocus();
  });

  it("applies the stored theme, motion and text scale to the root", () => {
    usePreferencesStore.setState({
      theme: "klassisk",
      reduceMotion: true,
      textScale: 150,
    });
    const { container } = renderShell();
    const root = container.querySelector(".xp-root")!;
    expect(root).toHaveAttribute("data-theme", "klassisk");
    expect(root).toHaveAttribute("data-reduce", "true");
    expect(root).toHaveStyle({ "--xp-font-size": "1.3125rem" });
  });

  it("sets data-reduce from prefers-reduced-motion", () => {
    mockMatchMedia({ "(prefers-reduced-motion: reduce)": true });
    const { container } = renderShell();
    expect(container.querySelector(".xp-root")).toHaveAttribute(
      "data-reduce",
      "true",
    );
  });

  it("is not in narrow mode on wide viewports", () => {
    mockMatchMedia({ "(max-width: 1023px)": false });
    const { container } = renderShell();
    expect(container.querySelector(".xp-root")).not.toHaveAttribute(
      "data-narrow",
    );
  });

  describe("narrow mode", () => {
    it("sets data-narrow and toggles the drawer from the menu button", async () => {
      const user = userEvent.setup();
      mockMatchMedia({ "(max-width: 1023px)": true });
      const { container } = renderShell();
      const root = container.querySelector(".xp-root")!;
      expect(root).toHaveAttribute("data-narrow", "true");
      expect(root).not.toHaveAttribute("data-drawer");

      const menu = screen.getByRole("button", { name: copy["header.menu"] });
      expect(menu).toHaveAttribute("aria-expanded", "false");
      await user.click(menu);
      expect(root).toHaveAttribute("data-drawer", "true");
      expect(menu).toHaveAttribute("aria-expanded", "true");
      expect(landmark("nav")).toHaveFocus();
    });

    it("closes the drawer with Esc and returns focus to the menu button", async () => {
      const user = userEvent.setup();
      mockMatchMedia({ "(max-width: 1023px)": true });
      const { container } = renderShell();
      const menu = screen.getByRole("button", { name: copy["header.menu"] });
      await user.click(menu);
      await user.keyboard("{Escape}");
      expect(container.querySelector(".xp-root")).not.toHaveAttribute(
        "data-drawer",
      );
      expect(menu).toHaveFocus();
    });

    it("drops the drawer when the viewport becomes wide again", async () => {
      const user = userEvent.setup();
      const media = mockMatchMedia({ "(max-width: 1023px)": true });
      const { container } = renderShell();
      await user.click(screen.getByRole("button", { name: copy["header.menu"] }));
      act(() => media.set("(max-width: 1023px)", false));
      const root = container.querySelector(".xp-root")!;
      expect(root).not.toHaveAttribute("data-narrow");
      expect(root).not.toHaveAttribute("data-drawer");
    });

    it("does not reopen the drawer when the viewport narrows again", async () => {
      const user = userEvent.setup();
      const media = mockMatchMedia({ "(max-width: 1023px)": true });
      const { container } = renderShell();
      await user.click(screen.getByRole("button", { name: copy["header.menu"] }));
      act(() => media.set("(max-width: 1023px)", false));
      act(() => media.set("(max-width: 1023px)", true));
      const root = container.querySelector(".xp-root")!;
      expect(root).toHaveAttribute("data-narrow", "true");
      expect(root).not.toHaveAttribute("data-drawer");
    });
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderShell();
    await expectNoSeriousViolations(container);
  });
});
