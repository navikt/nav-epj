import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { KontrollTemaPage } from "./KontrollTemaPage";
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

function renderPage() {
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
      <KontrollTemaPage />
    </ShellContext>,
  );
}

describe("KontrollTemaPage", () => {
  beforeEach(() => {
    announce.mockClear();
    usePreferencesStore.setState({
      theme: "luna",
      reduceMotion: false,
      textScale: 100,
    });
  });

  it("offers Luna and Klassisk as a labelled radio group with Luna selected", () => {
    renderPage();
    const group = screen.getByRole("radiogroup", { name: copy["s9.tema.theme"] });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: copy["s9.tema.luna"] })).toBeChecked();
    expect(
      screen.getByRole("radio", { name: copy["s9.tema.klassisk"] }),
    ).not.toBeChecked();
  });

  it("switches to Klassisk, stores it in the preferences store and announces it", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("radio", { name: copy["s9.tema.klassisk"] }),
    );
    expect(usePreferencesStore.getState().theme).toBe("klassisk");
    expect(localStorage.getItem(THEME_KEY)).toBe("klassisk");
    expect(announce).toHaveBeenCalledWith(
      copy["live.theme"](copy["s9.tema.klassisk"]),
    );
  });

  it("reflects and switches back from a stored Klassisk theme", async () => {
    usePreferencesStore.setState({ theme: "klassisk" });
    renderPage();
    expect(
      screen.getByRole("radio", { name: copy["s9.tema.klassisk"] }),
    ).toBeChecked();
    await userEvent.click(screen.getByRole("radio", { name: copy["s9.tema.luna"] }));
    expect(localStorage.getItem(THEME_KEY)).toBe("luna");
    expect(announce).toHaveBeenCalledWith(copy["live.theme"](copy["s9.tema.luna"]));
  });

  it("persists reduced motion and explains the operating system default", async () => {
    renderPage();
    const box = screen.getByRole("checkbox", { name: copy["s9.tema.reduce"] });
    expect(box).toHaveAccessibleDescription(copy["s9.tema.reduceHint"]);
    await userEvent.click(box);
    expect(localStorage.getItem(REDUCE_MOTION_KEY)).toBe("true");
    expect(box).toBeChecked();
  });

  it("persists the text size", async () => {
    renderPage();
    expect(
      screen.getByRole("radiogroup", { name: copy["s9.tema.text"] }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: copy["s9.tema.t100"] })).toBeChecked();
    await userEvent.click(screen.getByRole("radio", { name: copy["s9.tema.t150"] }));
    expect(localStorage.getItem(TEXT_SCALE_KEY)).toBe("150");
    expect(usePreferencesStore.getState().textScale).toBe(150);
  });

  it("says the choices are stored in this browser", () => {
    renderPage();
    expect(screen.getByText(copy["s9.tema.stored"])).toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPage();
    await expectNoSeriousViolations(container);
  });
});
