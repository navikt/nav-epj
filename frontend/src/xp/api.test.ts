import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  createPatient,
  fetchKonsultasjoner,
  fetchPatient,
  fetchPatients,
  saveKonsultasjon,
  startKonsultasjon,
} from "./api";

const pasient = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const konsultasjon = {
  id: "k1",
  pasientId: "p1",
  hpr: ["123"],
  journalnotat: [],
  diagnoser: [{ code: "A02", system: "ICPC2", text: "Frysninger" }],
  startetTidspunkt: "2026-09-30T09:00:00",
  avsluttetTidspunkt: null,
  status: "PÅGÅENDE",
  problemstilling: null,
};

function stub(body: unknown, ok = true, status = 200) {
  const fn = vi.fn(async () => ({ ok, status, json: async () => body }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe("api", () => {
  it("lists patients", async () => {
    const fn = stub([pasient]);
    expect(await fetchPatients()).toHaveLength(1);
    expect(fn).toHaveBeenCalledWith("/api/patient", undefined);
  });

  it("fetches one patient", async () => {
    const fn = stub(pasient);
    expect((await fetchPatient("p 1")).fornavn).toBe("Matematisk");
    expect(fn).toHaveBeenCalledWith("/api/patient/p%201", undefined);
  });

  it("creates a patient with a JSON body", async () => {
    const fn = stub(pasient);
    const body = {
      fornavn: "Matematisk",
      etternavn: "Ape",
      personident: "01019012345",
      personidentType: "FNR" as const,
      birthDate: "1990-01-01",
      gender: "MALE" as const,
    };
    await createPatient(body);
    expect(fn).toHaveBeenCalledWith("/api/patient", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  });

  it("lists konsultasjoner", async () => {
    const fn = stub([konsultasjon]);
    const result = await fetchKonsultasjoner("p1");
    expect(result[0].diagnoser[0].code).toBe("A02");
    expect(fn).toHaveBeenCalledWith("/api/patients/p1/konsultasjoner", undefined);
  });

  it("starts a konsultasjon with POST", async () => {
    const fn = stub(konsultasjon);
    await startKonsultasjon("p1");
    expect(fn).toHaveBeenCalledWith("/api/patients/p1/konsultasjoner", {
      method: "POST",
    });
  });

  it("saves a konsultasjon with PATCH and sends null for an empty note", async () => {
    const fn = stub({});
    await saveKonsultasjon("p1", {
      konsultasjonId: "k1",
      diagnoser: [{ kode: "A02", system: "ICPC2" }],
      journalNotat: null,
      ferdigstill: false,
    });
    const [, init] = fn.mock.calls[0] as unknown as [string, RequestInit];
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({
      konsultasjonId: "k1",
      diagnoser: [{ kode: "A02", system: "ICPC2" }],
      journalNotat: null,
      ferdigstill: false,
    });
  });

  it("throws ApiError with the status on a failed response", async () => {
    stub({}, false, 502);
    await expect(fetchPatients()).rejects.toMatchObject({ status: 502 });
    await expect(fetchPatients()).rejects.toBeInstanceOf(ApiError);
  });

  it("rejects unexpected payloads", async () => {
    stub([{ unexpectedField: 1 }]);
    await expect(fetchPatients()).rejects.toThrow();
  });
});
