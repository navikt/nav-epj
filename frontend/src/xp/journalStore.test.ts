import { act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useActivePatientStore } from "./activePatientStore";
import { useBalloonStore } from "./balloonStore";
import { isDirty, lacksMainDiagnosis, ongoingOf, useJournalStore } from "./journalStore";
import { usePatientsStore } from "./patientsStore";
import { useWorkspaceStore } from "./workspaceStore";

const patient = {
  id: "p1",
  givenName: "Matematisk",
  familyName: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: "1990-01-01",
  gender: "MALE",
};

const diag = { code: "A02", system: "ICPC2", text: "Frysninger" };
const a01 = { code: "A01", system: "ICPC2", text: "Smerte" };
const b02 = { code: "B02", system: "ICPC2", text: "Lymfeknuter" };

function consultation(overrides: Record<string, unknown> = {}) {
  return {
    id: "k1",
    patientId: "p1",
    hpr: ["9144889"],
    clinicalNotes: [],
    diagnoses: [],
    startedAt: "2026-09-30T09:00:00",
    endedAt: null,
    status: "IN_PROGRESS",
    reasonForVisit: null,
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
  it("loads the patient and consultations and derives the draft from the ongoing one", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({
        body: [
          consultation({
            diagnoses: [diag],
            clinicalNotes: [
              { id: "n1", consultationId: "k1", patientId: "p1", clinicalNote: "Hei" },
            ],
          }),
          consultation({ id: "k0", status: "FINISHED", startedAt: "2026-09-01T09:00:00" }),
        ],
      }),
    });
    await journal().open("p1");
    expect(journal().status).toBe("ready");
    expect(journal().patient?.givenName).toBe("Matematisk");
    expect(journal().draft).toEqual({
      mainDiagnosis: diag,
      secondaryDiagnoses: [],
      clinicalNote: "Hei",
    });
    expect(journal().draftConsultationId).toBe("k1");
    expect(isDirty(journal())).toBe(false);
    expect(ongoingOf(journal().consultations)?.id).toBe("k1");
    expect(useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal")).toMatchObject({
      label: "Journal · Matematisk Ape",
      unsaved: false,
    });
    expect(usePatientsStore.getState().recentIds).toEqual(["p1"]);
    expect(usePatientsStore.getState().lastConsultation["p1"]?.status).toBe("IN_PROGRESS");
  });

  it("reports which part failed to load", async () => {
    stub({
      "GET /api/patient/p1": () => ({ ok: false }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
    });
    await journal().open("p1");
    expect(journal()).toMatchObject({ status: "error", loadError: "patient" });

    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ ok: false }),
    });
    await journal().open("p1");
    expect(journal()).toMatchObject({ status: "error", loadError: "consultation" });
  });

  it("does not reload when the same patient is opened again, but updates the selection", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [consultation()] }),
    });
    await journal().open("p1");
    journal().setSubTab("previous");
    await journal().open("p1", "k0");
    expect(calls).toHaveLength(3);
    expect(journal().selectedConsultationId).toBe("k0");
    expect(journal().subTab).toBe("consultation");
    await journal().open("p1");
    expect(journal().selectedConsultationId).toBeNull();
  });

  it("makes the opened patient the active patient", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
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
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
      "PUT /api/active-patient": () => ({ ok: false }),
    });
    await journal().open("p1");
    expect(journal().status).toBe("error");
    expect(journal().loadError).toBe("patient");
  });

  it("reloads and drops the draft when another patient is opened", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [consultation()] }),
      "GET /api/patient/p2": () => ({ body: { ...patient, id: "p2", givenName: "Ola" } }),
      "GET /api/patients/p2/consultations": () => ({ body: [] }),
    });
    await journal().open("p1");
    journal().setClinicalNote("skisse");
    await journal().open("p2");
    expect(journal().patient?.givenName).toBe("Ola");
    expect(journal().draft.clinicalNote).toBe("");
    expect(journal().draftConsultationId).toBeNull();
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
          return { ok: true, status: 200, json: async () => patient };
        }
        if (url === "/api/patient/p2") {
          return { ok: true, status: 200, json: async () => ({ ...patient, id: "p2", givenName: "Ola" }) };
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
    expect(journal().patient?.givenName).toBe("Ola");
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
            return { ok: true, status: 200, json: async () => ({ ...patient, givenName: "First" }) };
          }
          return { ok: true, status: 200, json: async () => ({ ...patient, givenName: "Second" }) };
        }
        if (url === "/api/patient/p2") {
          return { ok: true, status: 200, json: async () => ({ ...patient, id: "p2" }) };
        }
        if (url === "/api/active-patient") {
          return { ok: true, status: 200, json: async () => activePatient };
        }
        return { ok: true, status: 200, json: async () => [consultation()] };
      }),
    );
    const first = journal().open("p1");
    await journal().open("p2");
    await journal().open("p1");
    journal().setClinicalNote("skrevet etterpå");
    releaseFirst();
    await first;
    expect(journal().patient?.givenName).toBe("Second");
    expect(journal().draft.clinicalNote).toBe("skrevet etterpå");
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
          return { ok: true, status: 200, json: async () => consultation({ id: "k9" }) };
        }
        return { ok: true, status: 200, json: async () => (url.endsWith("/consultations") ? [] : patient) };
      }),
    );
    await journal().open("p1");
    const starting = journal().start();
    journal().clear();
    releasePost();
    await starting;
    expect(journal().patientId).toBeNull();
    expect(journal().draftConsultationId).toBeNull();
  });

  describe("with a loaded ongoing consultation", () => {
    let calls: ReturnType<typeof stub>;
    let serverConsultation: ReturnType<typeof consultation>;

    beforeEach(async () => {
      serverConsultation = consultation({ diagnoses: [diag] });
      calls = stub({
        "GET /api/patient/p1": () => ({ body: patient }),
        "GET /api/patients/p1/consultations": () => ({ body: [serverConsultation] }),
        "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
      });
      await journal().open("p1");
    });

    const note = { id: "n1", consultationId: "k1", patientId: "p1", clinicalNote: "Ekstern" };
    const consultationCalls = () =>
      calls.filter((c) => c.key === "GET /api/patients/p1/consultations").length;

    it("turns an external note into the draft on a clean refresh without claiming again", async () => {
      journal().setSubTab("previous");
      const claims = calls.filter((c) => c.key === "PUT /api/active-patient").length;
      serverConsultation = consultation({ diagnoses: [diag], clinicalNotes: [note] });
      await journal().refresh();
      expect(journal().draft.clinicalNote).toBe("Ekstern");
      expect(isDirty(journal())).toBe(false);
      expect(journal().subTab).toBe("previous");
      expect(calls.filter((c) => c.key === "PUT /api/active-patient")).toHaveLength(claims);
    });

    it("skips refresh while the draft is dirty", async () => {
      journal().setClinicalNote("mitt");
      const before = consultationCalls();
      await journal().refresh();
      expect(consultationCalls()).toBe(before);
      expect(journal().draft.clinicalNote).toBe("mitt");
    });

    it("keeps edits made while a refresh is pending", async () => {
      serverConsultation = consultation({ clinicalNotes: [note] });
      const pending = journal().refresh();
      journal().setClinicalNote("mitt");
      await pending;
      expect(journal().draft.clinicalNote).toBe("mitt");
      expect(isDirty(journal())).toBe(true);
    });

    it("ignores a refresh response that arrives after the journal switched patient", async () => {
      serverConsultation = consultation({ clinicalNotes: [note] });
      const pending = journal().refresh();
      journal().clear();
      await pending;
      expect(journal().patientId).toBeNull();
      expect(journal().consultations).toEqual([]);
    });

    it("does not start a second refresh while one is pending", async () => {
      const before = consultationCalls();
      const first = journal().refresh();
      const second = journal().refresh();
      await Promise.all([first, second]);
      expect(consultationCalls()).toBe(before + 1);
    });

    it("keeps the loaded data and flags a failed refresh, then recovers on retry", async () => {
      const fetchMock = vi.mocked(fetch);
      const ok = fetchMock.getMockImplementation()!;
      fetchMock.mockImplementationOnce(async () => ({ ok: false, status: 500 }) as Response);
      await journal().refresh();
      expect(journal().refreshFailed).toBe(true);
      expect(journal().status).toBe("ready");
      expect(journal().consultations).toHaveLength(1);
      fetchMock.mockImplementation(ok);
      serverConsultation = consultation({ clinicalNotes: [note] });
      await journal().refresh();
      expect(journal().refreshFailed).toBe(false);
      expect(journal().draft.clinicalNote).toBe("Ekstern");
    });

    it("marks the tab unsaved when the draft differs and clears it when it matches again", () => {
      const tab = () => useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal");
      journal().setClinicalNote("ny tekst");
      expect(isDirty(journal())).toBe(true);
      expect(tab()?.unsaved).toBe(true);
      journal().setClinicalNote("");
      expect(tab()?.unsaved).toBe(false);
    });

    it("derives the main diagnosis from the first stored diagnosis and keeps the order of the rest", async () => {
      serverConsultation = consultation({ diagnoses: [b02, diag, a01] });
      journal().clear();
      await journal().open("p1");
      expect(journal().draft.mainDiagnosis).toEqual(b02);
      expect(journal().draft.secondaryDiagnoses).toEqual([diag, a01]);
      expect(isDirty(journal())).toBe(false);
    });

    it("adds each secondary diagnosis once and never next to the same main diagnosis", () => {
      journal().addSecondaryDiagnosis(a01);
      journal().addSecondaryDiagnosis(a01);
      journal().addSecondaryDiagnosis(diag);
      expect(journal().draft.mainDiagnosis).toEqual(diag);
      expect(journal().draft.secondaryDiagnoses).toEqual([a01]);
      journal().removeSecondaryDiagnosis("A01", "ICPC2");
      expect(journal().draft.secondaryDiagnoses).toEqual([]);
    });

    it("moves a secondary diagnosis to main and keeps the other secondaries", () => {
      journal().addSecondaryDiagnosis(a01);
      journal().addSecondaryDiagnosis(b02);
      journal().setMainDiagnosis(a01);
      expect(journal().draft.mainDiagnosis).toEqual(a01);
      expect(journal().draft.secondaryDiagnoses).toEqual([b02]);
      journal().setMainDiagnosis(a01);
      expect(journal().draft.secondaryDiagnoses).toEqual([b02]);
    });

    it("is dirty when only the role or the order of diagnoses changes", async () => {
      serverConsultation = consultation({ diagnoses: [diag, a01, b02] });
      journal().clear();
      await journal().open("p1");
      journal().removeSecondaryDiagnosis("A01", "ICPC2");
      journal().addSecondaryDiagnosis(a01);
      expect(journal().draft.secondaryDiagnoses).toEqual([b02, a01]);
      expect(isDirty(journal())).toBe(true);
      journal().discardDraft();
      expect(isDirty(journal())).toBe(false);
      journal().setMainDiagnosis(a01);
      journal().addSecondaryDiagnosis(diag);
      expect(journal().draft.secondaryDiagnoses).toEqual([b02, diag]);
      expect(isDirty(journal())).toBe(true);
    });

    it("saves the main diagnosis first and then the secondary ones in order", async () => {
      journal().addSecondaryDiagnosis(b02);
      journal().addSecondaryDiagnosis(a01);
      journal().setMainDiagnosis(a01);
      expect(await journal().save()).toBe(true);
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        diagnoses: [
          { code: "A01", system: "ICPC2" },
          { code: "B02", system: "ICPC2" },
        ],
      });
    });

    it("keeps secondaries when the main is removed but blocks save and finish without a request", async () => {
      journal().addSecondaryDiagnosis(a01);
      journal().removeMainDiagnosis();
      expect(journal().draft.mainDiagnosis).toBeNull();
      expect(journal().draft.secondaryDiagnoses).toEqual([a01]);
      const requests = calls.length;
      expect(await journal().save()).toBe(false);
      expect(await journal().save({ finalize: true })).toBe(false);
      expect(calls).toHaveLength(requests);
      expect(journal().saveStatus).toBe("idle");
      expect(lacksMainDiagnosis(journal().draft)).toBe(true);
    });

    it("saves with clinicalNote null when the note is blank", async () => {
      journal().setClinicalNote("   ");
      expect(await journal().save()).toBe(true);
      const patch = calls.find((c) => c.key.startsWith("PATCH"))!;
      expect(patch.body).toEqual({
        consultationId: "k1",
        diagnoses: [{ code: "A02", system: "ICPC2" }],
        clinicalNote: null,
        finalize: false,
      });
    });

    it("saves the note text and reloads the baseline from the server", async () => {
      journal().setClinicalNote("Pasienten er frisk");
      serverConsultation = consultation({
        diagnoses: [diag],
        clinicalNotes: [
          { id: "n1", consultationId: "k1", patientId: "p1", clinicalNote: "Pasienten er frisk" },
        ],
      });
      expect(await journal().save()).toBe(true);
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        clinicalNote: "Pasienten er frisk",
      });
      expect(journal().saveStatus).toBe("saved");
      expect(journal().savedAt).toBeInstanceOf(Date);
      expect(isDirty(journal())).toBe(false);
      expect(useBalloonStore.getState().balloon?.title).toBe("Konsultasjon lagret");
    });

    it("sends no diagnoses when the only chip was removed", async () => {
      journal().removeMainDiagnosis();
      serverConsultation = consultation({ diagnoses: [] });
      await journal().save();
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        diagnoses: [],
      });
      expect(journal().draft.mainDiagnosis).toBeNull();
      expect(journal().draft.secondaryDiagnoses).toEqual([]);
    });

    it("keeps the draft and flags the tab when saving fails", async () => {
      stub({
        "PATCH /api/patients/p1/consultations": () => ({ ok: false }),
      });
      journal().setClinicalNote("viktig");
      expect(await journal().save()).toBe(false);
      expect(journal().saveStatus).toBe("error");
      expect(journal().draft.clinicalNote).toBe("viktig");
      const tab = useWorkspaceStore.getState().tabs.find((t) => t.kind === "journal");
      expect(tab).toMatchObject({ unsaved: true, error: true });
    });

    it("finishes the consultation and turns it read-only", async () => {
      serverConsultation = consultation({
        diagnoses: [diag],
        status: "FINISHED",
        endedAt: "2026-09-30T09:30:00",
      });
      expect(await journal().save({ finalize: true })).toBe(true);
      expect(calls.find((c) => c.key.startsWith("PATCH"))!.body).toMatchObject({
        finalize: true,
      });
      expect(ongoingOf(journal().consultations)).toBeNull();
      expect(journal().draftConsultationId).toBeNull();
      expect(journal().selectedConsultationId).toBe("k1");
      expect(useBalloonStore.getState().balloon?.title).toBe("Konsultasjon fullført");
      expect(usePatientsStore.getState().lastConsultation["p1"]?.status).toBe("FINISHED");
    });

    it("falls back to a local update when the reload after saving fails", async () => {
      stub({
        "PATCH /api/patients/p1/consultations": () => ({ body: {} }),
        "GET /api/patients/p1/consultations": () => ({ ok: false }),
      });
      journal().setClinicalNote("lokalt");
      expect(await journal().save({ finalize: true })).toBe(true);
      expect(journal().consultations[0]).toMatchObject({ status: "FINISHED" });
      expect(journal().consultations[0].clinicalNotes.at(-1)?.clinicalNote).toBe("lokalt");
    });

    it("discards the draft back to the baseline", () => {
      journal().setClinicalNote("skisse");
      journal().removeMainDiagnosis();
      journal().discardDraft();
      expect(journal().draft).toEqual({
        mainDiagnosis: diag,
        secondaryDiagnoses: [],
        clinicalNote: "",
      });
      expect(isDirty(journal())).toBe(false);
    });

    it("does not save without an ongoing consultation", async () => {
      journal().clear();
      expect(await journal().save()).toBe(false);
    });
  });

  it("starts a consultation with POST and uses it as the draft target", async () => {
    const calls = stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
      "POST /api/patients/p1/consultations": () => ({ body: consultation({ id: "k9" }) }),
    });
    await journal().open("p1");
    await journal().start();
    expect(calls.some((c) => c.key === "POST /api/patients/p1/consultations")).toBe(true);
    expect(journal().draftConsultationId).toBe("k9");
    expect(journal().subTab).toBe("consultation");
    expect(journal().starting).toBe(false);
  });

  it("makes the patient active locally once a consultation has started", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
      "POST /api/patients/p1/consultations": () => ({ body: consultation({ id: "k9" }) }),
    });
    await journal().open("p1");
    useActivePatientStore.setState({ activeId: "p9" });
    await journal().start();
    expect(useActivePatientStore.getState().activeId).toBe("p1");
  });

  it("leaves the active patient alone when starting fails", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
      "POST /api/patients/p1/consultations": () => ({ ok: false }),
    });
    await journal().open("p1");
    useActivePatientStore.setState({ activeId: "p9" });
    await journal().start();
    expect(useActivePatientStore.getState().activeId).toBe("p9");
  });

  it("flags a failed start", async () => {
    stub({
      "GET /api/patient/p1": () => ({ body: patient }),
      "GET /api/patients/p1/consultations": () => ({ body: [] }),
      "POST /api/patients/p1/consultations": () => ({ ok: false }),
    });
    await journal().open("p1");
    await journal().start();
    expect(journal().startFailed).toBe(true);
    expect(journal().starting).toBe(false);
  });
});
