import { renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { copy } from "./copy";
import { usePatientsStore } from "./patientsStore";
import { usePatientName } from "./usePatientName";

const pasient = (id: string, fornavn: string) => ({
  id,
  fornavn,
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR" as const,
  birthDate: null,
  gender: null,
});

afterEach(() => vi.unstubAllGlobals());

describe("usePatientName", () => {
  it("uses the loaded patient list synchronously", () => {
    usePatientsStore.setState({ patients: [pasient("p2", "Ola")] });
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const { result } = renderHook(() => usePatientName("p2"));
    expect(result.current).toBe("Ola Ape");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a loading placeholder until the patient is fetched", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, status: 200, json: async () => pasient("p3", "Tore") })),
    );
    const { result } = renderHook(() => usePatientName("p3"));
    expect(result.current).toBe(copy["common.loading"]);
    await waitFor(() => expect(result.current).toBe("Tore Ape"));
  });

  it("falls back to the id when the patient cannot be fetched", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 404, json: async () => ({}) })));
    const { result } = renderHook(() => usePatientName("p4"));
    await waitFor(() => expect(result.current).toBe("p4"));
  });
});
