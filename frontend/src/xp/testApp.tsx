import { render } from "@testing-library/react";
import {
  RouterProvider,
  createBrowserHistory,
  createMemoryHistory,
  createRouter,
} from "@tanstack/react-router";
import { vi } from "vitest";
import { routeTree } from "../routeTree.gen";
import { mockMatchMedia } from "./matchMediaMock";

export const testMe = {
  hpr: "9144889",
  clinicId: "k1",
  name: "Kari Nordmann",
  qualification: "Lege",
};

export const testClinic = {
  id: "k1",
  name: "Storgata legekontor",
  orgNumber: "123456789",
  phone: null,
};

export const testSessionOrg = {
  org: testClinic,
  needsChoice: false,
  canChoose: false,
  choosableOrgs: [{ org: testClinic, hasOwnList: true }],
};

type Handler = (init?: RequestInit) => { status?: number; body?: unknown };

const activePatientHandler: Handler = (init) => ({
  body: {
    patientId: (JSON.parse(String(init?.body)) as { patientId: string }).patientId,
    expiresAt: "2026-09-30T17:14:00Z",
  },
});

export function stubApi(handlers: Record<string, Handler> = {}) {
  const calls: { key: string; init?: RequestInit }[] = [];
  const all: Record<string, Handler> = {
    "GET /api/practitioners/me": () => ({ body: testMe }),
    "GET /api/session-org": () => ({ body: testSessionOrg }),
    "PUT /api/active-patient": activePatientHandler,
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

export function renderApp(path: string, options: { browserHistory?: boolean } = {}) {
  if (options.browserHistory) window.history.replaceState(null, "", path);
  const router = createRouter({
    routeTree,
    history: options.browserHistory
      ? createBrowserHistory()
      : createMemoryHistory({ initialEntries: [path] }),
  });
  return { router, ...render(<RouterProvider router={router} />) };
}
