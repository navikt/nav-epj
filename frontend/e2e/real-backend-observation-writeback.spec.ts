import { createHash, randomBytes, randomInt, randomUUID } from "node:crypto";
import { expect, request as playwrightRequest, test } from "@playwright/test";
import type { APIRequestContext } from "@playwright/test";

const ENABLED = process.env.E2E_REAL_BACKEND === "1";
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? "http://localhost:8080";
const CLIENT_ID = process.env.E2E_SMART_CLIENT_ID ?? "syk-inn";
const REDIRECT_URI = process.env.E2E_SMART_REDIRECT_URI ?? "http://localhost:3000/fhir/callback";
const CLIENT_SECRET = process.env.E2E_SMART_CLIENT_SECRET;

const WRITE_SCOPE = "launch openid fhirUser patient/Observation.rs patient/Observation.write";
const READ_SCOPE = "launch patient/Observation.rs";
const DOC_WRITE_SCOPE = "launch patient/DocumentReference.rs patient/DocumentReference.c";
const DOC_READ_SCOPE = "launch patient/DocumentReference.rs";
const PDF_SCOPE = "launch patient/DocumentReference.cruds patient/QuestionnaireResponse.cruds";
const FHIR_JSON = "application/fhir+json";

type Patient = { id: string; givenName: string; familyName: string };
type Consultation = { id: string };
type ClinicalNote = { id: string; clinicalNote: string | null; source?: string };
type ConsultationWithNotes = Consultation & { clinicalNotes: ClinicalNote[] };
type Measurement = { id: string; value: number; unitCode: string; unitDisplay: string };
type Token = { access_token: string; patient?: string; encounter?: string; scope: string; token_type: string };

function assertLoopback(label: string, url: string | undefined) {
  const hostname = url ? new URL(url).hostname : "";
  if (!["localhost", "127.0.0.1", "[::1]"].includes(hostname)) {
    throw new Error(`${label} must be a loopback origin, got ${url}: this test mutates data through the local login stub`);
  }
}

function syntheticFnr(): { fnr: string; birthDate: string } {
  const base = [1, 5, 0, 5, 9, 0];
  for (;;) {
    const individ = randomInt(0, 500);
    const digits = [...base, Math.floor(individ / 100), Math.floor(individ / 10) % 10, individ % 10];
    const check = (weights: number[], ds: number[]) => {
      const sum = ds.reduce((acc, d, i) => acc + d * weights[i], 0);
      const k = 11 - (sum % 11);
      return k === 11 ? 0 : k;
    };
    const k1 = check([3, 7, 6, 1, 8, 9, 4, 5, 2], digits);
    if (k1 === 10) continue;
    const k2 = check([5, 4, 3, 2, 7, 6, 5, 4, 3, 2], [...digits, k1]);
    if (k2 === 10) continue;
    return { fnr: [...digits, k1, k2].join(""), birthDate: "1990-05-15" };
  }
}

function formatOslo(date: Date): string {
  const parts = new Intl.DateTimeFormat("nb-NO", {
    timeZone: "Europe/Oslo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${part("day")}.${part("month")}.${part("year")} ${part("hour")}:${part("minute")}`;
}

function observation(patientId: string, encounterId: string, effective: string, value: number) {
  return {
    resourceType: "Observation",
    status: "final",
    code: { coding: [{ system: "http://loinc.org", code: "8310-5", display: "Body temperature" }] },
    subject: { reference: `Patient/${patientId}` },
    encounter: { reference: `Encounter/${encounterId}` },
    effectiveDateTime: effective,
    valueQuantity: { value, unit: "degree Celsius", system: "http://unitsofmeasure.org", code: "Cel" },
  };
}

function documentReference(patientId: string, encounterId: string, description: string) {
  return {
    resourceType: "DocumentReference",
    status: "current",
    description,
    type: { coding: [{ system: "urn:oid:2.16.578.1.12.4.1.1.9602", code: "J01-2" }] },
    content: [{ attachment: { contentType: "application/pdf" } }],
    subject: { reference: `Patient/${patientId}` },
    context: { encounter: [{ reference: `Encounter/${encounterId}` }] },
  };
}

function buildPdf(text: string): Buffer {
  const stream = `BT /F1 18 Tf 72 720 Td (${text}) Tj ET`;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  ];
  let body = "%PDF-1.4\n";
  const offsets = objects.map((object, index) => {
    const offset = body.length;
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
    return offset;
  });
  const xref = body.length;
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`).join("");
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body, "latin1");
}

function questionnaireResponse(patientId: string, encounterId: string, id: string) {
  return {
    resourceType: "QuestionnaireResponse",
    id,
    status: "completed",
    questionnaire: "http://example.org/Questionnaire/sykmelding",
    authored: new Date().toISOString(),
    subject: { reference: `Patient/${patientId}` },
    encounter: { reference: `Encounter/${encounterId}` },
  };
}

function pdfDocumentReference(patientId: string, encounterId: string, id: string, relatedId: string, title: string) {
  const today = new Date().toISOString().slice(0, 10);
  return {
    resourceType: "DocumentReference",
    id,
    status: "current",
    type: { coding: [{ system: "urn:oid:2.16.578.1.12.4.1.1.9602", code: "J01-2" }] },
    subject: { reference: `Patient/${patientId}` },
    context: {
      encounter: [{ reference: `Encounter/${encounterId}` }],
      related: [{ reference: `QuestionnaireResponse/${relatedId}` }],
      period: { start: today, end: today },
    },
    content: [
      {
        attachment: {
          contentType: "application/pdf",
          language: "NO-nb",
          title,
          data: buildPdf("E2E sykmelding").toString("base64"),
        },
      },
    ],
  };
}

test.describe("FHIR write-back against the real backend", () => {
  test.skip(!ENABLED, "Set E2E_REAL_BACKEND=1 to run against a running backend, Postgres and Valkey");

  let api: APIRequestContext;
  let previousActive: string | null = null;
  const created: { patients: Patient[]; consultations: { patientId: string; id: string }[] } = {
    patients: [],
    consultations: [],
  };

  async function json<T>(promise: ReturnType<APIRequestContext["get"]>, status = 200): Promise<T> {
    const response = await promise;
    expect(response.status(), await response.text()).toBe(status);
    return (await response.json()) as T;
  }

  async function createPatient(label: string): Promise<Patient> {
    for (let attempt = 0; attempt < 5; attempt++) {
      const { fnr, birthDate } = syntheticFnr();
      const response = await api.post("/api/patient", {
        data: {
          givenName: "E2E",
          familyName: `${label}${randomBytes(3).toString("hex")}`,
          personident: fnr,
          personidentType: "FNR",
          birthDate,
          gender: Number(fnr[8]) % 2 === 0 ? "FEMALE" : "MALE",
        },
      });
      if (response.status() === 201) {
        const patient = (await response.json()) as Patient;
        created.patients.push(patient);
        return patient;
      }
      if (response.status() !== 409) {
        throw new Error(`POST /api/patient failed with ${response.status()}: ${await response.text()}`);
      }
    }
    throw new Error("POST /api/patient returned 409 (duplicate personident) on every attempt");
  }

  async function startConsultation(patient: Patient): Promise<Consultation> {
    const consultation = await json<Consultation>(
      api.post(`/api/patients/${patient.id}/consultations`),
    );
    created.consultations.push({ patientId: patient.id, id: consultation.id });
    return consultation;
  }

  async function measurements(patientId: string): Promise<Measurement[]> {
    return json<Measurement[]>(api.get(`/api/patient/${patientId}/measurements`));
  }

  async function clinicalNotes(patientId: string): Promise<ClinicalNote[]> {
    const list = await json<ConsultationWithNotes[]>(api.get(`/api/patients/${patientId}/consultations`));
    return list.flatMap((k) => k.clinicalNotes);
  }

  async function smartToken(patientId: string, scope: string): Promise<Token> {
    const config = await json<{ authorization_endpoint: string; token_endpoint: string }>(
      api.get("/fhir/.well-known/smart-configuration"),
    );
    assertLoopback("authorization_endpoint", config.authorization_endpoint);
    assertLoopback("token_endpoint", config.token_endpoint);

    const launch = await json<{ launchUrl: string }>(
      api.post("/api/launch", { data: { appId: CLIENT_ID, patientId } }),
    );
    const launchUrl = new URL(launch.launchUrl);
    const iss = launchUrl.searchParams.get("iss");
    const launchId = launchUrl.searchParams.get("launch");
    expect(iss).toBeTruthy();
    expect(launchId).toBeTruthy();

    const verifier = randomBytes(32).toString("base64url");
    const challenge = createHash("sha256").update(verifier).digest("base64url");
    const state = randomBytes(16).toString("hex");

    const authorize = new URL(config.authorization_endpoint);
    authorize.search = new URLSearchParams({
      response_type: "code",
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      scope,
      state,
      aud: iss!,
      launch: launchId!,
      code_challenge: challenge,
      code_challenge_method: "S256",
    }).toString();

    const redirect = await api.get(authorize.toString(), { maxRedirects: 0 });
    expect(redirect.status()).toBe(302);
    const location = new URL(redirect.headers()["location"]);
    expect(`${location.origin}${location.pathname}`).toBe(new URL(REDIRECT_URI).href);
    expect(location.searchParams.get("state")).toBe(state);
    expect(location.searchParams.get("error")).toBeNull();
    const code = location.searchParams.get("code");
    expect(code).toBeTruthy();

    const tokenResponse = await api.post(config.token_endpoint, {
      headers: {
        Authorization: `Basic ${Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString("base64")}`,
      },
      form: {
        grant_type: "authorization_code",
        code: code!,
        redirect_uri: REDIRECT_URI,
        code_verifier: verifier,
      },
    });
    expect(tokenResponse.status(), await tokenResponse.text()).toBe(200);
    const token = (await tokenResponse.json()) as Token;
    expect(token.token_type.toLowerCase()).toBe("bearer");
    expect(token.patient).toBe(patientId);
    return token;
  }

  const fhirHeaders = (token: Token) => ({
    Authorization: `Bearer ${token.access_token}`,
    "Content-Type": FHIR_JSON,
    Accept: FHIR_JSON,
  });

  test.beforeAll(async ({ baseURL }) => {
    assertLoopback("E2E_BACKEND_URL", BACKEND_URL);
    assertLoopback("Frontend baseURL", baseURL);
    if (!CLIENT_SECRET) {
      throw new Error("E2E_SMART_CLIENT_SECRET is required (local registration secret, never committed)");
    }
    api = await playwrightRequest.newContext({ baseURL: BACKEND_URL });
    const active = await api.get("/api/active-patient");
    if (active.status() === 200) {
      previousActive = ((await active.json()) as { patientId: string }).patientId;
    } else if (active.status() !== 204) {
      throw new Error(`GET /api/active-patient failed with ${active.status()}: ${await active.text()}`);
    }
  });

  test.afterAll(async () => {
    if (!api) return;
    const problems: string[] = [];
    for (const { patientId, id } of created.consultations) {
      const response = await api.post(`/api/patients/${patientId}/consultations/${id}/cancel`);
      if (response.status() !== 200) {
        problems.push(`cancel konsultasjon ${id} failed with ${response.status()}`);
      }
    }
    let restoration = "no previous active patient existed and there is no endpoint to clear it, so the last test patient stays active until it expires";
    if (previousActive) {
      const response = await api.put("/api/active-patient", { data: { patientId: previousActive } });
      if (response.status() === 200) {
        restoration = `previous active patient ${previousActive} restored`;
      } else {
        restoration = `previous active patient ${previousActive} NOT restored`;
        problems.push(`restore active patient failed with ${response.status()}`);
      }
    }
    console.log(
      `E2E test data left in the database (no patient or measurement delete API; app documents can be deleted through DELETE /api/clinical-notes/{id}): patients ${JSON.stringify(created.patients.map((p) => p.id))}, konsultasjoner ${JSON.stringify(created.consultations)}. ${restoration}.`,
    );
    await api.dispose();
    if (problems.length > 0) throw new Error(`E2E cleanup problems: ${problems.join("; ")}`);
  });

  test("writes an Observation through SMART auth and shows it in Målinger", async ({ page }) => {
    const target = await createPatient("Skrivetest");
    const other = await createPatient("Annenpasient");
    const otherConsultation = await startConsultation(other);
    const consultation = await startConsultation(target);
    expect(await measurements(target.id)).toEqual([]);
    expect(await measurements(other.id)).toEqual([]);

    const writeToken = await smartToken(target.id, WRITE_SCOPE);
    expect(writeToken.encounter).toBe(consultation.id);
    expect(writeToken.scope.split(" ")).toContain("patient/Observation.cud");

    const effective = new Date(Date.now() - 60_000);
    effective.setUTCSeconds(0, 0);
    const effectiveIso = effective.toISOString().replace(".000Z", "Z");
    const value = 36 + randomInt(10, 40) / 10;

    const created201 = await api.post("/fhir/Observation", {
      headers: fhirHeaders(writeToken),
      data: observation(target.id, consultation.id, effectiveIso, value),
    });
    expect(created201.status(), await created201.text()).toBe(201);
    const createdBody = (await created201.json()) as { id: string };
    expect(createdBody.id).toBeTruthy();
    const location = created201.headers()["location"];
    expect(location).toBe(`${BACKEND_URL}/fhir/Observation/${createdBody.id}`);

    const fetched = await api.get(`/fhir/Observation/${createdBody.id}`, { headers: fhirHeaders(writeToken) });
    expect(fetched.status()).toBe(200);
    expect(await fetched.json()).toMatchObject({
      resourceType: "Observation",
      id: createdBody.id,
      subject: { reference: `Patient/${target.id}` },
      encounter: { reference: `Encounter/${consultation.id}` },
      valueQuantity: { value, code: "Cel" },
    });

    const stored = await measurements(target.id);
    expect(stored).toHaveLength(1);
    expect(stored[0]).toMatchObject({ id: createdBody.id, value: value, unitCode: "Cel" });

    const readToken = await smartToken(target.id, READ_SCOPE);
    expect(readToken.scope.split(" ")).not.toContain("patient/Observation.cud");
    const crossPatientBody = observation(other.id, otherConsultation.id, effectiveIso, 37.1);
    const malformedBodies: { label: string; data?: unknown; body?: string }[] = [
      { label: "invalid JSON", body: "{not json" },
      {
        label: "wrong code system",
        data: {
          ...observation(target.id, consultation.id, effectiveIso, 37.5),
          code: { coding: [{ system: "http://example.org", code: "x", display: "x" }] },
        },
      },
    ];

    const countsBefore = [(await measurements(target.id)).length, (await measurements(other.id)).length];
    const rejected = [
      {
        label: "read-only token write",
        response: await api.post("/fhir/Observation", {
          headers: fhirHeaders(readToken),
          data: observation(target.id, consultation.id, effectiveIso, 37.2),
        }),
        status: [403],
      },
      {
        label: "cross-patient write with write token (patient mismatch is answered 404 by design)",
        response: await api.post("/fhir/Observation", {
          headers: fhirHeaders(writeToken),
          data: crossPatientBody,
        }),
        status: [404],
      },
      {
        label: "cross-patient write with read-only token",
        response: await api.post("/fhir/Observation", {
          headers: fhirHeaders(readToken),
          data: crossPatientBody,
        }),
        status: [403],
      },
      ...(await Promise.all(
        malformedBodies.map(async (m) => ({
          label: m.label,
          response: await api.post("/fhir/Observation", {
            headers: fhirHeaders(writeToken),
            ...(m.body !== undefined ? { data: m.body } : { data: m.data }),
          }),
          status: [400, 422],
        })),
      )),
    ];
    for (const { label, response, status } of rejected) {
      expect(status, `${label}: ${response.status()}`).toContain(response.status());
    }
    const countsAfter = [(await measurements(target.id)).length, (await measurements(other.id)).length];
    expect(countsAfter).toEqual(countsBefore);
    expect(countsAfter).toEqual([1, 0]);

    await page.goto(`/patients/${target.id}?tab=measurements`);
    const table = page.getByRole("table", { name: "Målinger" });
    await expect(table).toBeVisible();
    const rows = table.getByRole("row");
    await expect(rows).toHaveCount(2);
    const row = rows.nth(1);
    await expect(row).toContainText("Body temperature");
    await expect(row).toContainText(`${value} degree Celsius (Cel)`);
    await expect(row).toContainText(formatOslo(effective));
    await expect(row).toContainText("Endelig");
  });

  test("writes a DocumentReference through SMART auth and shows it in the journal", async ({ page }) => {
    const target = await createPatient("Notattest");
    const other = await createPatient("Annennotat");
    const otherConsultation = await startConsultation(other);
    const consultation = await startConsultation(target);
    expect(await clinicalNotes(target.id)).toEqual([]);
    expect(await clinicalNotes(other.id)).toEqual([]);

    const writeToken = await smartToken(target.id, DOC_WRITE_SCOPE);
    expect(writeToken.encounter).toBe(consultation.id);
    const writeGrants = writeToken.scope.split(" ");
    expect(writeGrants).toContain("patient/DocumentReference.rs");
    expect(writeGrants).toContain("patient/DocumentReference.c");
    const readToken = await smartToken(target.id, DOC_READ_SCOPE);
    const readGrants = readToken.scope.split(" ");
    expect(readGrants).toEqual(["launch", "patient/DocumentReference.rs"]);

    const consultationRequests: string[] = [];
    page.on("request", (request) => {
      if (request.url().includes("/consultations")) consultationRequests.push(request.url());
    });

    await page.goto(`/patients/${target.id}`);
    const editor = page.getByRole("textbox", { name: "Journalnotat" });
    await expect(editor).toBeVisible();
    await expect(editor).toHaveValue("");

    const text = `E2E journalnotat ${randomBytes(4).toString("hex")}`;
    const created201 = await api.post("/fhir/DocumentReference", {
      headers: fhirHeaders(writeToken),
      data: documentReference(target.id, consultation.id, text),
    });
    expect(created201.status(), await created201.text()).toBe(201);
    const createdBody = (await created201.json()) as { id: string };
    expect(createdBody.id).toBeTruthy();
    expect(created201.headers()["location"]).toBe(`${BACKEND_URL}/fhir/DocumentReference/${createdBody.id}`);

    const fetched = await api.get(`/fhir/DocumentReference/${createdBody.id}`, { headers: fhirHeaders(readToken) });
    expect(fetched.status(), await fetched.text()).toBe(200);
    expect(await fetched.json()).toMatchObject({
      resourceType: "DocumentReference",
      id: createdBody.id,
      description: text,
      subject: { reference: `Patient/${target.id}` },
      context: { encounter: [{ reference: `Encounter/${consultation.id}` }] },
    });

    const searched = await api.get(`/fhir/DocumentReference?patient=${target.id}`, { headers: fhirHeaders(readToken) });
    expect(searched.status(), await searched.text()).toBe(200);
    const bundle = (await searched.json()) as { resourceType: string; entry?: { resource: { id: string } }[] };
    expect(bundle.resourceType).toBe("Bundle");
    expect((bundle.entry ?? []).map((e) => e.resource.id)).toContain(createdBody.id);

    const stored = await clinicalNotes(target.id);
    expect(stored).toHaveLength(1);
    expect(stored[0]).toMatchObject({ id: createdBody.id, clinicalNote: text, source: "FHIR" });

    const draft = "ulagret utkast";
    await editor.fill(draft);
    const requestsBeforeReturn = consultationRequests.length;
    await page.evaluate(() => {
      window.dispatchEvent(new Event("focus"));
      document.dispatchEvent(new Event("visibilitychange"));
    });
    expect(consultationRequests.length).toBe(requestsBeforeReturn);
    await expect(editor).toHaveValue(draft);

    await editor.fill("");
    await page.getByRole("tab", { name: "Start" }).click();
    await page.getByRole("tab", { name: /^Journal/ }).click();
    await expect(page.getByRole("textbox", { name: "Journalnotat" })).toHaveValue("");
    await expect(page.getByRole("region", { name: "Dokumenter fra apper" })).toContainText(text);

    const countsBefore = [(await clinicalNotes(target.id)).length, (await clinicalNotes(other.id)).length];
    const readOnly = await api.post("/fhir/DocumentReference", {
      headers: fhirHeaders(readToken),
      data: documentReference(target.id, consultation.id, `${text} read-only`),
    });
    expect(readOnly.status(), await readOnly.text()).toBe(403);
    expect(readOnly.headers()["content-type"]).toContain("application/fhir+json");
    expect(await readOnly.json()).toMatchObject({
      resourceType: "OperationOutcome",
      issue: [{ severity: "error", code: "forbidden", diagnostics: "No granted scope covers DocumentReference.c" }],
    });

    const crossPatient = await api.post("/fhir/DocumentReference", {
      headers: fhirHeaders(writeToken),
      data: documentReference(other.id, otherConsultation.id, `${text} cross-patient`),
    });
    expect(crossPatient.status(), await crossPatient.text()).toBe(404);
    expect(crossPatient.headers()["content-type"]).toContain("application/fhir+json");
    expect(await crossPatient.json()).toMatchObject({
      resourceType: "OperationOutcome",
      issue: [{ severity: "error", code: "not-found", diagnostics: "Not found" }],
    });

    expect([(await clinicalNotes(target.id)).length, (await clinicalNotes(other.id)).length]).toEqual(countsBefore);
    expect(countsBefore).toEqual([1, 0]);
    expect(await clinicalNotes(target.id)).toEqual(stored);
  });

  test("writes a PDF with its questionnaire response and deletes both from the journal", async ({ page }) => {
    const target = await createPatient("Pdftest");
    const consultation = await startConsultation(target);
    const writeToken = await smartToken(target.id, PDF_SCOPE);
    expect(writeToken.encounter).toBe(consultation.id);

    const responseId = randomUUID();
    const documentId = randomUUID();
    const title = `sykmelding-${randomBytes(3).toString("hex")}.pdf`;

    const qr = await api.put(`/fhir/QuestionnaireResponse/${responseId}`, {
      headers: fhirHeaders(writeToken),
      data: questionnaireResponse(target.id, consultation.id, responseId),
    });
    expect(qr.status(), await qr.text()).toBe(201);

    const pdf = await api.put(`/fhir/DocumentReference/${documentId}`, {
      headers: fhirHeaders(writeToken),
      data: pdfDocumentReference(target.id, consultation.id, documentId, responseId, title),
    });
    expect(pdf.status(), await pdf.text()).toBe(201);

    const siblingText = `E2E søsterdokument ${randomBytes(4).toString("hex")}`;
    const sibling = await api.post("/fhir/DocumentReference", {
      headers: fhirHeaders(writeToken),
      data: documentReference(target.id, consultation.id, siblingText),
    });
    expect(sibling.status(), await sibling.text()).toBe(201);

    await page.goto(`/patients/${target.id}`);
    const editor = page.getByRole("textbox", { name: "Journalnotat" });
    await expect(editor).toHaveValue("");
    const documents = page.getByRole("region", { name: "Dokumenter fra apper" });
    const link = documents.getByRole("link", { name: `Åpne ${title} (PDF, ny fane)` });
    await expect(link).toHaveAttribute("href", `/api/clinical-notes/${documentId}/attachment`);
    await expect(documents).toContainText(siblingText);

    const download = await api.get(`/api/clinical-notes/${documentId}/attachment`);
    expect(download.status()).toBe(200);
    expect(download.headers()["content-type"]).toContain("application/pdf");
    expect(Buffer.from(await download.body()).subarray(0, 5).toString()).toBe("%PDF-");

    const draft = "ulagret utkast";
    await editor.fill(draft);

    await documents.getByRole("button", { name: `Slett ${title}` }).click();
    const dialog = page.getByRole("alertdialog");
    await expect(dialog).toContainText("QuestionnaireResponse");
    await dialog.getByRole("button", { name: "Avbryt" }).click();
    await expect(dialog).toBeHidden();
    await expect(link).toBeVisible();

    await documents.getByRole("button", { name: `Slett ${title}` }).click();
    await page.getByRole("alertdialog").getByRole("button", { name: "Slett", exact: true }).click();
    await expect(page.getByRole("alertdialog")).toBeHidden();

    await expect(link).toHaveCount(0);
    await expect(documents).toContainText(siblingText);
    await expect(editor).toHaveValue(draft);

    const remaining = await clinicalNotes(target.id);
    expect(remaining.map((n) => n.clinicalNote)).toEqual([siblingText]);
    const gone = await api.get(`/fhir/QuestionnaireResponse/${responseId}`, { headers: fhirHeaders(writeToken) });
    expect(gone.status()).toBe(404);
    const goneDocument = await api.get(`/fhir/DocumentReference/${documentId}`, { headers: fhirHeaders(writeToken) });
    expect(goneDocument.status()).toBe(404);
  });
});
