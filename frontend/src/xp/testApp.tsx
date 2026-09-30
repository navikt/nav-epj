import { render } from "@testing-library/react";
import {
  RouterProvider,
  createMemoryHistory,
  createRouter,
} from "@tanstack/react-router";
import { vi } from "vitest";
import { routeTree } from "../routeTree.gen";
import { mockMatchMedia } from "./matchMediaMock";

export const testMe = {
  hpr: "9144889",
  legekontorId: "k1",
  navn: "Kari Nordmann",
  autorisasjon: "Lege",
};

export const testKontor = {
  id: "k1",
  navn: "Storgata legekontor",
  orgnummer: "123456789",
  tlf: null,
};

type Handler = (init?: RequestInit) => { status?: number; body?: unknown };

export function stubApi(handlers: Record<string, Handler> = {}) {
  const calls: { key: string; init?: RequestInit }[] = [];
  const all: Record<string, Handler> = {
    "GET /api/helsepersonell/me": () => ({ body: testMe }),
    "GET /api/legekontor/k1": () => ({ body: testKontor }),
    ...handlers,
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const key = `${init?.method ?? "GET"} ${url}`;
      calls.push({ key, init });
      const handler = all[key];
      const result = handler ? handler(init) : { status: 404, body: {} };
      const status = result.status ?? 200;
      return { ok: status < 400, status, json: async () => result.body };
    }),
  );
  return calls;
}

export function setupBrowserStubs() {
  mockMatchMedia({});
  vi.stubGlobal("scrollTo", vi.fn());
}

export function renderApp(path: string) {
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  return { router, ...render(<RouterProvider router={router} />) };
}
