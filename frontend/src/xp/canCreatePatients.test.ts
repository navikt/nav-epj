import { afterEach, describe, expect, it, vi } from "vitest";
import { canCreatePatients } from "./canCreatePatients";

function stubHostname(hostname: string) {
  vi.stubGlobal("location", { ...window.location, hostname });
}

describe("canCreatePatients", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("is true on localhost", () => {
    stubHostname("localhost");
    expect(canCreatePatients()).toBe(true);
  });

  it("is false on dev", () => {
    stubHostname("nav-epj.ekstern.dev.nav.no");
    expect(canCreatePatients()).toBe(false);
  });

  it("is false on unknown hosts instead of throwing", () => {
    stubHostname("example.org");
    expect(canCreatePatients()).toBe(false);
  });
});
