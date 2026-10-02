import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  RouterProvider,
  createMemoryHistory,
  createRouter,
} from "@tanstack/react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { routeTree } from "../routeTree.gen";
import { mockMatchMedia } from "./matchMediaMock";
import { copy } from "./copy";
import { expectNoSeriousViolations } from "./axeHelper";
import { useWorkspaceStore } from "./workspaceStore";

const me = { hpr: "9144889", legekontorId: "k1", navn: "Kari Nordmann", autorisasjon: "Lege" };
const kontor = { id: "k1", navn: "Storgata legekontor", orgnummer: "123456789", tlf: null };

function stubApi(meResponse: () => Promise<unknown>) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) => {
      if (url === "/api/helsepersonell/me") return meResponse();
      const body = url.startsWith("/api/legekontor/") ? kontor : [];
      return Promise.resolve({ ok: true, status: 200, json: async () => body });
    }),
  );
}

const okMe = () =>
  Promise.resolve({ ok: true, status: 200, json: async () => me });

function renderApp(path: string) {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  return { router, ...render(<RouterProvider router={router} />) };
}

beforeEach(() => {
  mockMatchMedia({});
  vi.stubGlobal("scrollTo", vi.fn());
  useWorkspaceStore.getState().reset();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("root route shell", () => {
  it.each(["/", "/patients"])("renders %s inside the shell", async (path) => {
    stubApi(okMe);
    renderApp(path);
    expect(await screen.findByRole("banner")).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: copy["nav.label"] })).toBeInTheDocument();
    expect(screen.getByRole("main")).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole("main")).not.toHaveTextContent(copy["s1.loading"]),
    );
    expect(screen.getByRole("tab", { name: copy["tabs.start"] })).toBeInTheDocument();
  });

  it("selects the Pasienter tab and labels the panel with it on /patients", async () => {
    stubApi(okMe);
    renderApp("/patients");
    const tab = await screen.findByRole("tab", {
      name: copy["pane.system.patients"],
    });
    expect(tab).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: copy["tabs.start"] })).toHaveAttribute(
      "aria-selected",
      "false",
    );
    expect(screen.getByRole("tabpanel")).toHaveAccessibleName(
      copy["pane.system.patients"],
    );
  });

  it("opens Pasienter from the task pane and returns to Start from the tab strip", async () => {
    const user = userEvent.setup();
    stubApi(okMe);
    const { router } = renderApp("/");
    await screen.findByText(copy["header.user"](me.navn, me.autorisasjon, kontor.navn));
    await user.click(
      within(screen.getByRole("navigation", { name: copy["nav.label"] })).getByText(
        copy["pane.system.patients"],
      ),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
    expect(
      await screen.findByRole("tab", { name: copy["pane.system.patients"], selected: true }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: copy["tabs.start"] }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
    expect(screen.getByRole("tab", { name: copy["tabs.start"] })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("opens Pasienter when Enter is pressed in the search field", async () => {
    const user = userEvent.setup();
    stubApi(okMe);
    const { router } = renderApp("/");
    await screen.findByText(copy["header.user"](me.navn, me.autorisasjon, kontor.navn));
    await user.type(screen.getByLabelText(copy["header.search.label"]), "Kari{Enter}");
    await waitFor(() => expect(router.state.location.pathname).toBe("/patients"));
    expect(
      await screen.findByRole("tab", { name: copy["pane.system.patients"], selected: true }),
    ).toBeInTheDocument();
  });

  it("shows the loading state until the user is loaded", async () => {
    let resolve: (value: unknown) => void = () => {};
    stubApi(() => new Promise((r) => (resolve = r)));
    renderApp("/");
    expect(await screen.findByText(copy["s1.loading"])).toBeInTheDocument();
    resolve({ ok: true, status: 200, json: async () => me });
    expect(await screen.findByText(copy["header.user"](me.navn, me.autorisasjon, kontor.navn))).toBeInTheDocument();
    expect(screen.queryByText(copy["s1.loading"])).not.toBeInTheDocument();
  });

  it("shows the error state and recovers on retry", async () => {
    const user = userEvent.setup();
    stubApi(() => Promise.resolve({ ok: false, status: 502, json: async () => ({}) }));
    renderApp("/");
    expect(await screen.findByRole("alert")).toHaveTextContent(copy["s1.error.title"]);
    stubApi(okMe);
    await user.click(screen.getByRole("button", { name: copy["s1.error.retry"] }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  });

  it("has no serious accessibility violations", async () => {
    stubApi(okMe);
    const { container } = renderApp("/");
    await screen.findByText(copy["header.user"](me.navn, me.autorisasjon, kontor.navn));
    await expectNoSeriousViolations(container);
  });
});
