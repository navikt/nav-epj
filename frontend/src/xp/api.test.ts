import { afterEach, describe, expect, it, vi } from "vitest";
import { useAppDialogStore } from "./appDialogStore";
import { useSessionStore } from "./sessionExpiry";
import {
  ApiError,
  createPatient,
  fetchKonsultasjoner,
  fetchPatient,
  fetchApps,
  fetchPatients,
  LaunchError,
  launchApp,
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

  it("lists registered apps", async () => {
    const fn = stub([
      {
        clientId: "syk-inn",
        navn: "Sykmelding",
        beskrivelse: null,
        ikon: "sykmelding",
        launchMode: "iframe",
        launchUri: "https://syk.example",
        tokenEndpointAuthMethod: "client_secret_basic",
        jwksUri: null,
        redirectUris: [],
        scopes: ["launch"],
      },
    ]);
    const apps = await fetchApps();
    expect(apps[0].launchMode).toBe("iframe");
    expect(fn).toHaveBeenCalledWith("/api/apps", undefined);
  });

  it("rejects an app with an unknown launch mode", async () => {
    stub([{ clientId: "x", navn: "X", ikon: "vindu", launchMode: "popup" }]);
    await expect(fetchApps()).rejects.toThrow();
  });

  it("launches an app and returns the launch url", async () => {
    const fn = stub({ launchUrl: "https://syk.example/?launch=1" });
    expect(await launchApp("syk-inn")).toBe("https://syk.example/?launch=1");
    expect(fn).toHaveBeenCalledWith("/api/launch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ appId: "syk-inn" }),
    });
  });

  it.each(["NO_ACTIVE_PATIENT", "NO_ACTIVE_ENCOUNTER", "UNKNOWN_APP"] as const)(
    "throws a typed error for %s",
    async (code) => {
      stub({ code, message: "m", appId: "syk-inn" }, false, 409);
      await expect(launchApp("syk-inn")).rejects.toMatchObject({
        code,
        status: 409,
      });
      await expect(launchApp("syk-inn")).rejects.toBeInstanceOf(LaunchError);
    },
  );

  it("throws a plain ApiError when the error body is unrecognised", async () => {
    stub({ nope: true }, false, 502);
    const error = await launchApp("syk-inn").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(LaunchError);
    expect((error as ApiError).status).toBe(502);
  });

  it("expires the session when any call returns 401", async () => {
    stub({}, false, 401);
    await expect(fetchPatients()).rejects.toMatchObject({ status: 401 });
    expect(useSessionStore.getState().expired).toBe(true);
    expect(useAppDialogStore.getState().dialog).toMatchObject({
      kind: "error",
      code: "SESSION_EXPIRED",
      call: "GET /api/patient",
    });
  });

  it("expires the session when the launch call returns 401", async () => {
    stub({}, false, 401);
    await expect(launchApp("syk-inn")).rejects.toMatchObject({ status: 401 });
    expect(useSessionStore.getState().expired).toBe(true);
  });
});
