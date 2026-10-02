import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useHelsepersonell } from "./useHelsepersonell";

const me = { hpr: "9144889", legekontorId: "k1", navn: "Kari", autorisasjon: "Lege" };
const kontor = { id: "k1", navn: "Storgata legekontor", orgnummer: "123456789", tlf: null };

function respond(body: unknown, ok = true) {
  return Promise.resolve({ ok, status: ok ? 200 : 502, json: async () => body });
}

function stubFetch(handler: (url: string) => Promise<unknown>) {
  const fn = vi.fn((url: string) => handler(url));
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe("useHelsepersonell", () => {
  it("starts loading and resolves helsepersonell with legekontor", async () => {
    const fetchMock = stubFetch((url) =>
      respond(url.startsWith("/api/legekontor/") ? kontor : me),
    );
    const { result } = renderHook(() => useHelsepersonell());
    expect(result.current.state.status).toBe("loading");
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
    expect(result.current.state).toMatchObject({
      helsepersonell: me,
      legekontor: kontor,
    });
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      "/api/helsepersonell/me",
      "/api/legekontor/k1",
    ]);
  });

  it("errors on a non-ok response", async () => {
    stubFetch(() => respond({}, false));
    const { result } = renderHook(() => useHelsepersonell());
    await waitFor(() => expect(result.current.state.status).toBe("error"));
  });

  it("errors on an unexpected payload", async () => {
    stubFetch(() => respond({ unexpected: 1 }));
    const { result } = renderHook(() => useHelsepersonell());
    await waitFor(() => expect(result.current.state.status).toBe("error"));
  });

  it("errors when the network fails", async () => {
    stubFetch(() => Promise.reject(new TypeError("offline")));
    const { result } = renderHook(() => useHelsepersonell());
    await waitFor(() => expect(result.current.state.status).toBe("error"));
  });

  it("retries after an error", async () => {
    let fail = true;
    stubFetch((url) => {
      if (fail) return respond({}, false);
      return respond(url.startsWith("/api/legekontor/") ? kontor : me);
    });
    const { result } = renderHook(() => useHelsepersonell());
    await waitFor(() => expect(result.current.state.status).toBe("error"));
    fail = false;
    act(() => result.current.retry());
    expect(result.current.state.status).toBe("loading");
    await waitFor(() => expect(result.current.state.status).toBe("ready"));
  });
});
