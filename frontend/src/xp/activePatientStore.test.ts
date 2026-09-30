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
});
