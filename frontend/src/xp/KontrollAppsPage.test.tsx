import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { KontrollAppsPage } from "./KontrollAppsPage";
import { nyFane, seedApps, sykInn, validator } from "./appFixtures";
import { useAppsStore } from "./appsStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

describe("KontrollAppsPage", () => {
  beforeEach(() => seedApps([sykInn, { ...validator, tokenEndpointAuthMethod: "private_key_jwt" }, nyFane]));

  it("lists every registered app with client_id, view and client authentication", () => {
    render(<KontrollAppsPage />);
    const table = screen.getByRole("table", { name: copy["s9.cat.apps"] });
    expect(
      within(table).getAllByRole("columnheader").map((h) => h.textContent),
    ).toEqual([
      copy["s9.apps.col.app"],
      copy["s9.apps.col.id"],
      copy["s9.apps.col.mode"],
      copy["s9.apps.col.auth"],
      copy["s9.apps.props"],
    ]);
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText("Sykmelding")).toBeInTheDocument();
    expect(within(rows[0]).getByText("syk-inn")).toBeInTheDocument();
    expect(within(rows[0]).getByText(copy["pane.apps.mode.iframe"])).toBeInTheDocument();
    expect(within(rows[0]).getByText("client_secret_basic")).toBeInTheDocument();
    expect(within(rows[1]).getByText("private_key_jwt")).toBeInTheDocument();
    expect(within(rows[1]).getByText(copy["pane.apps.mode.ask"])).toBeInTheDocument();
    expect(within(rows[2]).getByText(copy["pane.apps.mode.tab"])).toBeInTheDocument();
  });

  it("says that secrets are never shown", () => {
    render(<KontrollAppsPage />);
    expect(screen.getByText(copy["s9.apps.note"])).toBeInTheDocument();
  });

  it("opens the properties dialog for the chosen app and returns focus on close", async () => {
    render(<KontrollAppsPage />);
    const buttons = screen.getAllByRole("button", { name: copy["s9.apps.props"] });
    expect(buttons[0]).toHaveAccessibleDescription(/Sykmelding/);
    await userEvent.click(buttons[1]);
    expect(
      screen.getByRole("dialog", { name: copy["s10.title"]("Validator") }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: copy["common.ok"] }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(buttons[1]).toHaveFocus();
  });

  it("shows loading while the apps are being fetched", () => {
    useAppsStore.setState({ status: "loading", apps: [] });
    render(<KontrollAppsPage />);
    expect(screen.getByRole("status")).toHaveTextContent(copy["common.loading"]);
  });

  it("shows an error with a retry that reloads the apps", async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [],
    }));
    vi.stubGlobal("fetch", fetchMock);
    useAppsStore.setState({ status: "error", apps: [] });
    render(<KontrollAppsPage />);
    expect(screen.getByRole("alert")).toHaveTextContent(copy["s8.NETWORK.head"]);
    await userEvent.click(screen.getByRole("button", { name: copy["s8.NETWORK.action"] }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/apps", undefined));
    vi.unstubAllGlobals();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<KontrollAppsPage />);
    await expectNoSeriousViolations(container);
  });
});
