import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppearancePanel } from "./AppearancePanel";
import { ShellContext } from "./shellContext";
import {
  REDUCE_MOTION_KEY,
  TEXT_SCALE_KEY,
  THEME_KEY,
  usePreferencesStore,
} from "./preferencesStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

const announce = vi.fn();

function renderPanel() {
  return render(
    <ShellContext
      value={{
        narrow: false,
        drawerOpen: false,
        setDrawerOpen: vi.fn(),
        announce,
        rootElement: null,
      }}
    >
      <AppearancePanel />
    </ShellContext>,
  );
}

describe("AppearancePanel", () => {
  beforeEach(() => {
    announce.mockClear();
    usePreferencesStore.setState({
      theme: "luna",
      reduceMotion: false,
      textScale: 100,
    });
  });

  it("switches to Klassisk, persists it and announces the change", async () => {
    renderPanel();
    await userEvent.click(
      screen.getByRole("checkbox", { name: copy["s9.tema.klassisk"] }),
    );
    expect(localStorage.getItem(THEME_KEY)).toBe("klassisk");
    expect(announce).toHaveBeenCalledWith(
      copy["live.theme"](copy["s9.tema.klassisk"]),
    );
  });

  it("switches back to Luna", async () => {
    usePreferencesStore.setState({ theme: "klassisk" });
    renderPanel();
    const box = screen.getByRole("checkbox", { name: copy["s9.tema.klassisk"] });
    expect(box).toBeChecked();
    await userEvent.click(box);
    expect(localStorage.getItem(THEME_KEY)).toBe("luna");
    expect(announce).toHaveBeenCalledWith(
      copy["live.theme"](copy["s9.tema.luna"]),
    );
  });

  it("persists reduce motion", async () => {
    renderPanel();
    await userEvent.click(
      screen.getByRole("checkbox", { name: copy["s9.tema.reduce"] }),
    );
    expect(localStorage.getItem(REDUCE_MOTION_KEY)).toBe("true");
  });

  it("offers text sizes in a labelled radio group", async () => {
    renderPanel();
    const group = screen.getByRole("group", { name: copy["s9.tema.text"] });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: copy["s9.tema.t100"] })).toBeChecked();
    await userEvent.click(screen.getByRole("radio", { name: copy["s9.tema.t125"] }));
    expect(localStorage.getItem(TEXT_SCALE_KEY)).toBe("125");
    expect(screen.getByRole("radio", { name: copy["s9.tema.t125"] })).toBeChecked();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPanel();
    await expectNoSeriousViolations(container);
  });
});
