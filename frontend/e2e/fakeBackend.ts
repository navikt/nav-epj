import type { BrowserContext, Route } from "@playwright/test";

type Launch = { appId: string; patientId: string };

const patients = [
  { id: "p1", givenName: "Ola", familyName: "Nordmann", birthDate: "1990-01-01" },
  { id: "p2", givenName: "Kari", familyName: "Hansen", birthDate: "1985-05-05" },
].map((p) => ({
  ...p,
  personident: `0101901234${p.id.slice(1)}`,
  personidentType: "FNR",
  gender: "MALE",
  selectable: true,
}));

const clinic = { id: "k1", name: "Storgata legekontor", orgNumber: "123456789", phone: null };

const app = {
  clientId: "syk-inn",
  navn: "Sykmelding",
  beskrivelse: null,
  ikon: "sykmelding",
  launchMode: "iframe",
  launchUri: "https://syk.example/fhir",
  tokenEndpointAuthMethod: "client_secret_basic",
  jwksUri: null,
  redirectUris: [],
  scopes: ["launch"],
};

const validator = {
  ...app,
  clientId: "validator",
  navn: "Validator",
  beskrivelse: "Tester SMART on FHIR-oppstart og FHIR-API-et mot nav-epj, med en beskrivelse som brekker over flere linjer.",
  ikon: "validator",
  launchUri: "https://validator.example/fhir",
};

export function fakeBackend({ withHistory = false, ongoing = true } = {}) {
  const state = { activeId: null as string | null, launches: [] as Launch[] };
  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

  async function install(context: BrowserContext) {
    await context.route("https://syk.example/**", (route) =>
      route.fulfill({ contentType: "text/html", body: "<html><body>app</body></html>" }),
    );
    await context.route("**/api/**", async (route) => {
      const request = route.request();
      const { pathname } = new URL(request.url());
      const key = `${request.method()} ${pathname}`;
      const body = request.postDataJSON() as Record<string, string> | null;
      const patientId = pathname.match(/^\/api\/patients?\/([^/]+)/)?.[1];
      const known = patients.find((p) => p.id === patientId);
      if (key === "GET /api/practitioners/me") {
        return json(route, { hpr: "9144889", clinicId: "k1", name: "Kari Lege", qualification: "Lege" });
      }
      if (key === "GET /api/clinics/k1") return json(route, clinic);
      if (key === "GET /api/session-org") {
        return json(route, { org: clinic, needsChoice: false, canChoose: false, choosableOrgs: [] });
      }
      if (key === "GET /api/patient") return json(route, patients);
      if (key === "GET /api/apps") return json(route, [app, validator]);
      if (key === `GET /api/patient/${patientId}` && known) return json(route, known);
      if (key === `GET /api/patients/${patientId}/consultations` && known) {
        return json(route, [
          ...(ongoing
            ? [
                {
                  id: `k-${known.id}`,
                  patientId: known.id,
                  hpr: ["9144889"],
                  clinicalNotes: [],
                  diagnoses: [],
                  startedAt: "2026-09-30T09:14:00",
                  endedAt: null,
                  status: "IN_PROGRESS",
                  reasonForVisit: null,
                },
              ]
            : []),
          ...(withHistory
            ? [
                {
                  id: `h-${known.id}`,
                  patientId: known.id,
                  hpr: ["9144889"],
                  clinicalNotes: [
                    { id: "n1", consultationId: `h-${known.id}`, patientId: known.id, clinicalNote: "Kontroll, ingen funn." },
                  ],
                  diagnoses: [{ code: "L87", system: "ICPC-2", text: "Hypertensjon" }],
                  startedAt: "2026-08-12T09:40:00",
                  endedAt: "2026-08-12T10:05:00",
                  status: "FINISHED",
                  reasonForVisit: "Kontroll",
                },
              ]
            : []),
        ]);
      }
      if (key === "GET /api/active-patient") {
        if (!state.activeId) return route.fulfill({ status: 204 });
        return json(route, { patientId: state.activeId, expiresAt: "2099-01-01T00:00:00Z" });
      }
      if (key === "PUT /api/active-patient" && body) {
        state.activeId = body.patientId;
        return json(route, { patientId: state.activeId, expiresAt: "2099-01-01T00:00:00Z" });
      }
      if (key === "POST /api/launch" && body) {
        const error = (status: number, code: string) =>
          json(route, { code, message: code, appId: body.appId }, status);
        if (!state.activeId) return error(409, "NO_ACTIVE_PATIENT");
        if (state.activeId !== body.patientId) return error(409, "PATIENT_MISMATCH");
        state.launches.push({ appId: body.appId, patientId: body.patientId });
        return json(route, {
          launchUrl: `https://syk.example/fhir?iss=https://epj.example/fhir&launch=id-${state.launches.length}`,
        });
      }
      return json(route, {}, 404);
    });
  }

  return { state, install };
}
