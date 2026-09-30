import { afterEach, describe, expect, it, vi } from "vitest";
import { scopePatients, usePatientsStore } from "./patientsStore";
import type { Pasient } from "../utils/mapping/epj";

const pasient = (id: string): Pasient => ({
  id,
  fornavn: `Fornavn${id}`,
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: null,
  gender: null,
});

const store = () => usePatientsStore.getState();

function stubFetch(body: unknown, ok = true) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({ ok, status: ok ? 200 : 500, json: async () => body })),
  );
}

describe("patientsStore", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("loads patients and reports ready", async () => {
    stubFetch([pasient("1")]);
    await store().load();
    expect(store().status).toBe("ready");
    expect(store().patients).toHaveLength(1);
  });

  it("reports error when the request fails", async () => {
    stubFetch([], false);
    await store().load();
    expect(store().status).toBe("error");
  });

  it("resets the page when the query or view changes", () => {
    store().setPage(3);
    store().setQuery("ape");
    expect(store().page).toBe(1);
    store().setPage(2);
    store().setView("recent");
    expect(store().page).toBe(1);
  });

  it("keeps the most recent ids first, deduplicated, and persists them per clinician", () => {
    store().setOwner("h1");
    store().markOpened("1");
    store().markOpened("2");
    store().markOpened("1");
    expect(store().recentIds).toEqual(["1", "2"]);
    expect(JSON.parse(localStorage.getItem("nav-epj:recent:h1")!)).toEqual(["1", "2"]);
    expect(localStorage.getItem("nav-epj:recent:h2")).toBeNull();
  });

  it("shows only the signed-in clinician's recent ids and drops the legacy shared key", () => {
    localStorage.setItem("nav-epj:recent", JSON.stringify(["old"]));
    localStorage.setItem("nav-epj:recent:h1", JSON.stringify(["a"]));
    localStorage.setItem("nav-epj:recent:h2", JSON.stringify(["b"]));
    store().setOwner("h2");
    expect(store().recentIds).toEqual(["b"]);
    expect(localStorage.getItem("nav-epj:recent")).toBeNull();
    store().setOwner("h1");
    expect(store().recentIds).toEqual(["a"]);
    store().setOwner(null);
    expect(store().recentIds).toEqual([]);
  });

  it("keeps recent ids in memory only until the owner is known", () => {
    store().markOpened("1");
    expect(store().recentIds).toEqual(["1"]);
    expect(localStorage.length).toBe(0);
  });

  it("does not throw when persisting recent ids fails", () => {
    store().setOwner("h1");
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => store().markOpened("1")).not.toThrow();
    expect(store().recentIds).toEqual(["1"]);
    setItem.mockRestore();
  });

  it("caps recent ids at ten", () => {
    for (let i = 0; i < 12; i++) store().markOpened(String(i));
    expect(store().recentIds).toHaveLength(10);
    expect(store().recentIds[0]).toBe("11");
  });

  it("tracks and clears the last konsultasjon per patient", () => {
    store().setLastKonsultasjon("1", { status: "FULLFØRT", tidspunkt: "2026-01-01T10:00:00" });
    expect(store().lastKonsultasjon["1"].status).toBe("FULLFØRT");
    store().setLastKonsultasjon("1", null);
    expect(store().lastKonsultasjon["1"]).toBeUndefined();
  });

  it("scopes to recent patients that still exist, in recent order", () => {
    const patients = [pasient("1"), pasient("2"), pasient("3")];
    expect(scopePatients(patients, ["3", "gone", "1"], "recent").map((p) => p.id)).toEqual([
      "3",
      "1",
    ]);
    expect(scopePatients(patients, ["3"], "mine")).toHaveLength(3);
  });
});
