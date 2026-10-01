import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppHeader } from "./AppHeader";
import { AppShell } from "./AppShell";
import { usePatientsStore } from "./patientsStore";
import { mockMatchMedia } from "./matchMediaMock";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

const user = {
  navn: "Ola Nordmann",
  autorisasjon: "Lege",
  legekontor: "Legekontoret",
};

function renderHeader(
  onLogout = vi.fn(),
  currentUser: typeof user | null = user,
  onSearchSubmit = vi.fn(),
) {
  return {
    onLogout,
    onSearchSubmit,
    ...render(
      <AppShell>
        <AppHeader
          user={currentUser}
          onLogout={onLogout}
          onSearchSubmit={onSearchSubmit}
        />
      </AppShell>,
    ),
  };
}

describe("AppHeader", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("shows brand, subtitle and the TEST marker", () => {
    renderHeader();
    const header = screen.getByRole("banner");
    expect(within(header).getByText(copy["app.name"])).toBeInTheDocument();
    expect(within(header).getByText(copy["app.subtitle"])).toBeInTheDocument();
    expect(
      within(header).getByTitle(copy["app.testTooltip"]),
    ).toHaveTextContent(copy["app.test"]);
  });

  it("shows the logged in user", () => {
    renderHeader();
    expect(
      screen.getByText(
        copy["header.user"](user.navn, user.autorisasjon, user.legekontor),
      ),
    ).toBeInTheDocument();
  });

  it("omits user info while it is not available", () => {
    renderHeader(vi.fn(), null);
    expect(screen.queryByText(/Ola Nordmann/)).not.toBeInTheDocument();
  });

  it("exposes a labelled search inside a search landmark", () => {
    renderHeader();
    const search = screen.getByRole("search");
    const input = within(search).getByLabelText(copy["header.search.label"]);
    expect(input).toHaveAttribute(
      "placeholder",
      copy["header.search.placeholder"],
    );
  });

  it("does not spell-check the search, which accepts fødselsnumre", () => {
    renderHeader();
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveAttribute(
      "spellcheck",
      "false",
    );
  });

  it("uses the short placeholder in narrow mode", () => {
    mockMatchMedia({ "(max-width: 1023px)": true });
    renderHeader();
    expect(
      screen.getByLabelText(copy["header.search.label"]),
    ).toHaveAttribute("placeholder", copy["header.search.placeholderNarrow"]);
  });

  it("focuses the search with Ctrl+Shift+P", async () => {
    const u = userEvent.setup();
    renderHeader();
    await u.keyboard("{Control>}{Shift>}p{/Shift}{/Control}");
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveFocus();
  });

  it("focuses the search with Cmd+Shift+P (Mac)", () => {
    renderHeader();
    fireEvent.keyDown(document, {
      key: "p",
      metaKey: true,
      shiftKey: true,
    });
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveFocus();
  });

  it("clears the search with Esc", async () => {
    const u = userEvent.setup();
    const { onSearchSubmit } = renderHeader();
    const input = screen.getByLabelText(copy["header.search.label"]);
    await u.type(input, "Kari");
    await u.keyboard("{Escape}");
    expect(input).toHaveValue("");
    expect(onSearchSubmit).not.toHaveBeenCalled();
  });

  it("opens Pasienter on Enter and keeps the typed query", async () => {
    const u = userEvent.setup();
    const { onSearchSubmit } = renderHeader();
    const input = screen.getByLabelText(copy["header.search.label"]);
    await u.type(input, "Kari{Enter}");
    expect(onSearchSubmit).toHaveBeenCalledOnce();
    expect(input).toHaveValue("Kari");
  });

  it("shares the typed query with the patient list", async () => {
    const u = userEvent.setup();
    renderHeader();
    await u.type(screen.getByLabelText(copy["header.search.label"]), "Kari");
    expect(usePatientsStore.getState().query).toBe("Kari");
    act(() => usePatientsStore.getState().setQuery(""));
    expect(screen.getByLabelText(copy["header.search.label"])).toHaveValue("");
  });

  it("calls onLogout from the logout button", async () => {
    const { onLogout } = renderHeader();
    await userEvent.click(
      screen.getByRole("button", { name: copy["header.logout"] }),
    );
    expect(onLogout).toHaveBeenCalledOnce();
  });

  it("toggles the drawer from the menu button", async () => {
    const u = userEvent.setup();
    mockMatchMedia({ "(max-width: 1023px)": true });
    renderHeader();
    const menu = screen.getByRole("button", { name: copy["header.menu"] });
    expect(menu).toHaveAttribute("aria-expanded", "false");
    expect(menu).toHaveAttribute("aria-controls", "xp-task-pane");
    await u.click(menu);
    expect(menu).toHaveAttribute("aria-expanded", "true");
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderHeader();
    await expectNoSeriousViolations(container);
  });
});
