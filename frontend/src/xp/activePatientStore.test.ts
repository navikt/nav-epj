import { afterEach, describe, expect, it, vi } from "vitest";
import { useActivePatientStore } from "./activePatientStore";

function stub(responses: Array<{ status?: number; body?: unknown } | "network">) {
  const queue = [...responses];
  const fn = vi.fn(async () => {
    const next = queue.shift();
    if (next === "network" || next === undefined) throw new TypeError("network");
    const status = next.status ?? 200;
    return { ok: status < 400, status, json: async () => next.body };
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

const active = (patientId: string) => ({
  body: { patientId, expiresAt: "2026-09-30T17:14:00Z" },
});

afterEach(() => vi.unstubAllGlobals());

describe("activePatientStore", () => {
  it("stores the active patient read from the server", async () => {
    stub([active("p2")]);
    await useActivePatientStore.getState().refresh();
    expect(useActivePatientStore.getState().activeId).toBe("p2");
  });

  it("clears the active patient on 204", async () => {
    useActivePatientStore.setState({ activeId: "p1" });
    stub([{ status: 204 }]);
    await useActivePatientStore.getState().refresh();
    expect(useActivePatientStore.getState().activeId).toBeNull();
  });

  it("keeps the last known value when the check fails", async () => {
    useActivePatientStore.setState({ activeId: "p1" });
    stub(["network"]);
    await useActivePatientStore.getState().refresh();
    expect(useActivePatientStore.getState().activeId).toBe("p1");
  });

  it("ignores a check that resolves after the patient was set locally", async () => {
    let release: () => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        await new Promise<void>((resolve) => {
          release = resolve;
        });
        return { ok: true, status: 200, json: async () => active("p1").body };
      }),
    );
    const pending = useActivePatientStore.getState().refresh();
    useActivePatientStore.getState().setActive("p2");
    release();
    await pending;
    expect(useActivePatientStore.getState().activeId).toBe("p2");
  });

  it("lets the newest of two overlapping checks win", async () => {
    const releases: Array<() => void> = [];
    const answers = [active("p1").body, active("p2").body];
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        const answer = answers.shift();
        await new Promise<void>((resolve) => releases.push(resolve));
        return { ok: true, status: 200, json: async () => answer };
      }),
    );
    const first = useActivePatientStore.getState().refresh();
    const second = useActivePatientStore.getState().refresh();
    releases[1]();
    await second;
    releases[0]();
    await first;
    expect(useActivePatientStore.getState().activeId).toBe("p2");
  });

  it("claims a patient on the server and stores it", async () => {
    const fn = stub([active("p2")]);
    const result = await useActivePatientStore.getState().claim("p2");
    expect(result.patientId).toBe("p2");
    expect(useActivePatientStore.getState().activeId).toBe("p2");
    expect(fn).toHaveBeenCalledWith(
      "/api/active-patient",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("leaves the store alone when a claim fails", async () => {
    useActivePatientStore.setState({ activeId: "p1" });
    stub([{ status: 404 }]);
    await expect(useActivePatientStore.getState().claim("p2")).rejects.toMatchObject({
      status: 404,
    });
    expect(useActivePatientStore.getState().activeId).toBe("p1");
  });

  it("ignores an older claim that resolves after a newer one", async () => {
    const releases: Array<() => void> = [];
    const bodies = [active("p1").body, active("p2").body];
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        const body = bodies.shift();
        await new Promise<void>((resolve) => releases.push(resolve));
        return { ok: true, status: 200, json: async () => body };
      }),
    );
    const first = useActivePatientStore.getState().claim("p1");
    const second = useActivePatientStore.getState().claim("p2");
    releases[1]();
    await second;
    releases[0]();
    await first;
    expect(useActivePatientStore.getState().activeId).toBe("p2");
  });

  it("tells other windows about a local change", async () => {
    const received: unknown[] = [];
    const listener = new BroadcastChannel("nav-epj:active-patient");
    listener.onmessage = (event) => received.push(event.data);
    useActivePatientStore.getState().setActive("p3");
    await vi.waitFor(() => expect(received).toEqual(["p3"]));
    listener.close();
  });
});
