import { act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useActivePatientStore } from "./activePatientStore";
import { useBalloonStore } from "./balloonStore";
import { isDirty, ongoingOf, useJournalStore } from "./journalStore";
import { usePatientsStore } from "./patientsStore";
import { useWorkspaceStore } from "./workspaceStore";

const pasient = {
  id: "p1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const diag = { code: "A02", system: "ICPC2", text: "Frysninger" };

function kons(overrides: Record<string, unknown> = {}) {
  return {
    id: "k1",
    pasientId: "p1",
    hpr: ["9144889"],
    journalnotat: [],
    diagnoser: [],
    startetTidspunkt: "2026-09-30T09:00:00",
    avsluttetTidspunkt: null,
    status: "PÅGÅENDE",
    problemstilling: null,
    ...overrides,
  };
}

type Routes = Record<string, (init?: RequestInit) => { ok?: boolean; body?: unknown }>;

function stub(routes: Routes) {
  const calls: { key: string; body?: unknown }[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const key = `${init?.method ?? "GET"} ${url}`;
      calls.push({ key, body: init?.body ? JSON.parse(init.body as string) : undefined });
      const result =
        routes[key]?.(init) ??
        (key === "PUT /api/active-patient"
          ? { body: { patientId: "p1", expiresAt: "2026-09-30T17:14:00Z" } }
          : { ok: false });
      const ok = result.ok ?? true;
      return { ok, status: ok ? 200 : 500, json: async () => result.body };
    }),
  );
  return calls;
}

const activePatient = { patientId: "p1", expiresAt: "2026-09-30T17:14:00Z" };

const journal = () => useJournalStore.getState();

beforeEach(() => {
  act(() => {
    useWorkspaceStore.getState().openTab({ kind: "journal", label: "Journal" });
  });
});

afterEach(() => {
  useJournalStore.getState().clear();
  vi.unstubAllGlobals();
});

describe("journalStore", () => {
  it("loads the patient and konsultasjoner and derives the draft from the ongoing one", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({
        body: [
          kons({
            diagnoser: [diag],
            journalnotat: [
              { id: "n1", konsultasjonId: "k1", pasientId: "p1", journalnotat: "Hei" },
            ],
          }),
          kons({ id: "k0", status: "FULLFØRT", startetTidspunkt: "2026-09-01T09:00:00" }),
        ],
      }),
    });
    await journal().open("p1");
    expect(journal().status).toBe("ready");
    expect(journal().patient?.fornavn).toBe("Matematisk");
    expect(journal().draft).toEqual({ diagnoser: [diag], notat: "Hei" });
    expect(journal().draftKonsultasjonId).toBe("k1");
    expect(isDirty(journal())).toBe(false);
    expect(ongoingOf(journal().konsultasjoner)?.id).toBe("k1");
    expect(useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal")).toMatchObject({
      label: "Journal · Matematisk Ape",
      unsaved: false,
    });
    expect(usePatientsStore.getState().recentIds).toEqual(["p1"]);
    expect(usePatientsStore.getState().lastKonsultasjon["p1"]?.status).toBe("PÅGÅENDE");
  });

  it("reports which part failed to load", async () => {
    stub({
      "GET /api/patient/p1": () => ({ ok: false }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
    });
    await journal().open("p1");
    expect(journal()).toMatchObject({ status: "error", loadError: "patient" });

    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ ok: false }),
    });
    await journal().open("p1");
    expect(journal()).toMatchObject({ status: "error", loadError: "kons" });
  });

  it("does not reload when the same patient is opened again, but updates the selection", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [kons()] }),
    });
    await journal().open("p1");
    journal().setSubTab("tidligere");
    await journal().open("p1", "k0");
    expect(calls).toHaveLength(3);
    expect(journal().selectedKonsultasjonId).toBe("k0");
    expect(journal().subTab).toBe("konsultasjon");
    await journal().open("p1");
    expect(journal().selectedKonsultasjonId).toBeNull();
  });

  it("makes the opened patient the active patient", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
    });
    await journal().open("p1");
    expect(calls).toContainEqual({
      key: "PUT /api/active-patient",
      body: { patientId: "p1" },
    });
    expect(journal().status).toBe("ready");
  });

  it("does not open a journal when the active patient could not be set", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
      "PUT /api/active-patient": () => ({ ok: false }),
    });
    await journal().open("p1");
    expect(journal().status).toBe("error");
    expect(journal().loadError).toBe("patient");
  });

  it("reloads and drops the draft when another patient is opened", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [kons()] }),
      "GET /api/patient/p2": () => ({ body: { ...pasient, id: "p2", fornavn: "Ola" } }),
      "GET /api/patients/p2/konsultasjoner": () => ({ body: [] }),
    });
    await journal().open("p1");
    journal().setNotat("skisse");
    await journal().open("p2");
    expect(journal().patient?.fornavn).toBe("Ola");
    expect(journal().draft.notat).toBe("");
    expect(journal().draftKonsultasjonId).toBeNull();
  });

  it("ignores a stale load that finishes after another patient was opened", async () => {
    let releaseP1: () => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url === "/api/patient/p1") {
          await new Promise<void>((resolve) => {
            releaseP1 = resolve;
          });
          return { ok: true, status: 200, json: async () => pasient };
        }
        if (url === "/api/patient/p2") {
          return { ok: true, status: 200, json: async () => ({ ...pasient, id: "p2", fornavn: "Ola" }) };
        }
        if (url === "/api/active-patient") {
          return { ok: true, status: 200, json: async () => activePatient };
        }
        return { ok: true, status: 200, json: async () => [] };
      }),
    );
    const first = journal().open("p1");
    await journal().open("p2");
    releaseP1();
    await first;
    expect(journal().patientId).toBe("p2");
    expect(journal().patient?.fornavn).toBe("Ola");
  });

  it("ignores an older load of the same patient that resolves after a newer one", async () => {
    let releaseFirst: () => void = () => {};
    let patientCalls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url === "/api/patient/p1") {
          patientCalls += 1;
          if (patientCalls === 1) {
            await new Promise<void>((resolve) => {
              releaseFirst = resolve;
            });
            return { ok: true, status: 200, json: async () => ({ ...pasient, fornavn: "First" }) };
          }
          return { ok: true, status: 200, json: async () => ({ ...pasient, fornavn: "Second" }) };
        }
        if (url === "/api/patient/p2") {
          return { ok: true, status: 200, json: async () => ({ ...pasient, id: "p2" }) };
        }
        if (url === "/api/active-patient") {
          return { ok: true, status: 200, json: async () => activePatient };
        }
        return { ok: true, status: 200, json: async () => [kons()] };
      }),
    );
    const first = journal().open("p1");
    await journal().open("p2");
    await journal().open("p1");
    journal().setNotat("skrevet etterpå");
    releaseFirst();
    await first;
    expect(journal().patient?.fornavn).toBe("Second");
    expect(journal().draft.notat).toBe("skrevet etterpå");
  });

  it("drops the result of a start that finishes after the journal was cleared", async () => {
    let releasePost: () => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === "POST") {
          await new Promise<void>((resolve) => {
            releasePost = resolve;
          });
          return { ok: true, status: 200, json: async () => kons({ id: "k9" }) };
        }
        return { ok: true, status: 200, json: async () => (url.endsWith("/konsultasjoner") ? [] : pasient) };
      }),
    );
    await journal().open("p1");
    const starting = journal().start();
    journal().clear();
    releasePost();
    await starting;
    expect(journal().patientId).toBeNull();
    expect(journal().draftKonsultasjonId).toBeNull();
  });

  describe("with a loaded ongoing konsultasjon", () => {
    let calls: ReturnType<typeof stub>;
    let serverKons: ReturnType<typeof kons>;

    beforeEach(async () => {
      serverKons = kons({ diagnoser: [diag] });
      calls = stub({
        "GET /api/patient/p1": () => ({ body: pasient }),
        "GET /api/patients/p1/konsultasjoner": () => ({ body: [serverKons] }),
        "PATCH /api/patients/p1/konsultasjoner": () => ({ body: {} }),
      });
      await journal().open("p1");
    });

    const note = { id: "n1", konsultasjonId: "k1", pasientId: "p1", journalnotat: "Ekstern" };
    const konsCalls = () =>
      calls.filter((c) => c.key === "GET /api/patients/p1/konsultasjoner").length;

    it("turns an external note into the draft on a clean refresh without claiming again", async () => {
      journal().setSubTab("tidligere");
      const claims = calls.filter((c) => c.key === "PUT /api/active-patient").length;
      serverKons = kons({ diagnoser: [diag], journalnotat: [note] });
      await journal().refresh();
      expect(journal().draft.notat).toBe("Ekstern");
      expect(isDirty(journal())).toBe(false);
      expect(journal().subTab).toBe("tidligere");
      expect(calls.filter((c) => c.key === "PUT /api/active-patient")).toHaveLength(claims);
    });

    it("skips refresh while the draft is dirty", async () => {
      journal().setNotat("mitt");
      const before = konsCalls();
      await journal().refresh();
      expect(konsCalls()).toBe(before);
      expect(journal().draft.notat).toBe("mitt");
    });

    it("keeps edits made while a refresh is pending", async () => {
      serverKons = kons({ journalnotat: [note] });
      const pending = journal().refresh();
      journal().setNotat("mitt");
      await pending;
      expect(journal().draft.notat).toBe("mitt");
      expect(isDirty(journal())).toBe(true);
    });

    it("ignores a refresh response that arrives after the journal switched patient", async () => {
      serverKons = kons({ journalnotat: [note] });
      const pending = journal().refresh();
      journal().clear();
      await pending;
      expect(journal().patientId).toBeNull();
      expect(journal().konsultasjoner).toEqual([]);
    });

    it("does not start a second refresh while one is pending", async () => {
      const before = konsCalls();
      const first = journal().refresh();
      const second = journal().refresh();
      await Promise.all([first, second]);
      expect(konsCalls()).toBe(before + 1);
    });

    it("keeps the loaded data and flags a failed refresh, then recovers on retry", async () => {
      const fetchMock = vi.mocked(fetch);
      const ok = fetchMock.getMockImplementation()!;
      fetchMock.mockImplementationOnce(async () => ({ ok: false, status: 500 }) as Response);
      await journal().refresh();
      expect(journal().refreshFailed).toBe(true);
      expect(journal().status).toBe("ready");
      expect(journal().konsultasjoner).toHaveLength(1);
      fetchMock.mockImplementation(ok);
      serverKons = kons({ journalnotat: [note] });
      await journal().refresh();
      expect(journal().refreshFailed).toBe(false);
      expect(journal().draft.notat).toBe("Ekstern");
    });

    it("marks the tab unsaved when the draft differs and clears it when it matches again", () => {
      const tab = () => useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal");
      journal().setNotat("ny tekst");
      expect(isDirty(journal())).toBe(true);
      expect(tab()?.unsaved).toBe(true);
      journal().setNotat("");
      expect(tab()?.unsaved).toBe(false);
    });

    it("adds each diagnosis once and removes by code and system", () => {
      const a01 = { code: "A01", system: "ICPC2", text: "Smerte" };
      journal().addDiagnose(a01);
      journal().addDiagnose(a01);
      expect(journal().draft.diagnoser).toHaveLength(2);
      journal().removeDiagnose("A02", "ICPC2");
      expect(journal().draft.diagnoser).toEqual([a01]);
      expect(isDirty(journal())).toBe(true);
    });

    it("ignores diagnosis order when comparing to the baseline", () => {
      journal().removeDiagnose("A02", "ICPC2");
      journal().addDiagnose(diag);
      expect(isDirty(journal())).toBe(false);
    });

    it("saves with journalNotat null when the note is blank", async () => {
      journal().setNotat("   ");
      expect(await journal().save()).toBe(true);
      const patch = calls.find((c) => c.key.startsWith("PATCH"))!;
      expect(patch.body).toEqual({
        konsultasjonId: "k1",
        diagnoser: [{ kode: "A02", system: "ICPC2" }],
        journalNotat: null,
        ferdigstill: false,
      });
    });

    it("saves the note text and reloads the baseline from the server", async () => {
      journal().setNotat("Pasienten er frisk");
      serverKons = kons({
        diagnoser: [diag],
        journalnotat: [
          { id: "n1", konsultasjonId: "k1", pasientId: "p1", journalnotat: "Pasienten er frisk" },
        ],
      });
      expect(await journal().save()).toBe(true);
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        journalNotat: "Pasienten er frisk",
      });
      expect(journal().saveStatus).toBe("saved");
      expect(journal().savedAt).toBeInstanceOf(Date);
      expect(isDirty(journal())).toBe(false);
      expect(useBalloonStore.getState().balloon?.title).toBe("Konsultasjon lagret");
    });

    it("sends fewer diagnoses when a chip was removed", async () => {
      journal().removeDiagnose("A02", "ICPC2");
      serverKons = kons({ diagnoser: [] });
      await journal().save();
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        diagnoser: [],
      });
      expect(journal().draft.diagnoser).toEqual([]);
    });

    it("keeps the draft and flags the tab when saving fails", async () => {
      stub({
        "PATCH /api/patients/p1/konsultasjoner": () => ({ ok: false }),
      });
      journal().setNotat("viktig");
      expect(await journal().save()).toBe(false);
      expect(journal().saveStatus).toBe("error");
      expect(journal().draft.notat).toBe("viktig");
      const tab = useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal");
      expect(tab).toMatchObject({ unsaved: true, error: true });
    });

    it("finishes the konsultasjon and turns it read-only", async () => {
      serverKons = kons({
        diagnoser: [diag],
        status: "FULLFØRT",
        avsluttetTidspunkt: "2026-09-30T09:30:00",
      });
      expect(await journal().save({ ferdigstill: true })).toBe(true);
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        ferdigstill: true,
      });
      expect(ongoingOf(journal().konsultasjoner)).toBeNull();
      expect(journal().draftKonsultasjonId).toBeNull();
      expect(journal().selectedKonsultasjonId).toBe("k1");
      expect(useBalloonStore.getState().balloon?.title).toBe("Konsultasjon fullført");
      expect(usePatientsStore.getState().lastKonsultasjon["p1"]?.status).toBe("FULLFØRT");
    });

    it("falls back to a local update when the reload after saving fails", async () => {
      stub({
        "PATCH /api/patients/p1/konsultasjoner": () => ({ body: {} }),
        "GET /api/patients/p1/konsultasjoner": () => ({ ok: false }),
      });
      journal().setNotat("lokalt");
      expect(await journal().save({ ferdigstill: true })).toBe(true);
      expect(journal().konsultasjoner[0]).toMatchObject({ status: "FULLFØRT" });
      expect(journal().konsultasjoner[0].journalnotat.at(-1)?.journalnotat).toBe("lokalt");
    });

    it("discards the draft back to the baseline", () => {
      journal().setNotat("skisse");
      journal().removeDiagnose("A02", "ICPC2");
      journal().discardDraft();
      expect(journal().draft).toEqual({ diagnoser: [diag], notat: "" });
      expect(isDirty(journal())).toBe(false);
    });

    it("does not save without an ongoing konsultasjon", async () => {
      journal().clear();
      expect(await journal().save()).toBe(false);
    });
  });

  it("starts a konsultasjon with POST and uses it as the draft target", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
      "POST /api/patients/p1/konsultasjoner": () => ({ body: kons({ id: "k9" }) }),
    });
    await journal().open("p1");
    await journal().start();
    expect(calls.some((c) => c.key === "POST /api/patients/p1/konsultasjoner")).toBe(true);
    expect(journal().draftKonsultasjonId).toBe("k9");
    expect(journal().subTab).toBe("konsultasjon");
    expect(journal().starting).toBe(false);
  });

  it("makes the patient active locally once a konsultasjon has started", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
      "POST /api/patients/p1/konsultasjoner": () => ({ body: kons({ id: "k9" }) }),
    });
    await journal().open("p1");
    useActivePatientStore.setState({ activeId: "p9" });
    await journal().start();
    expect(useActivePatientStore.getState().activeId).toBe("p1");
  });

  it("leaves the active patient alone when starting fails", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
      "POST /api/patients/p1/konsultasjoner": () => ({ ok: false }),
    });
    await journal().open("p1");
    useActivePatientStore.setState({ activeId: "p9" });
    await journal().start();
    expect(useActivePatientStore.getState().activeId).toBe("p9");
  });

  it("flags a failed start", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: pasient }),
      "GET /api/patients/p1/konsultasjoner": () => ({ body: [] }),
      "POST /api/patients/p1/konsultasjoner": () => ({ ok: false }),
    });
    await journal().open("p1");
    await journal().start();
    expect(journal().startFailed).toBe(true);
    expect(journal().starting).toBe(false);
  });
});
