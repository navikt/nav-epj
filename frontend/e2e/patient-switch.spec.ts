import { expect, test, type BrowserContext, type Route } from "@playwright/test";

type Launch = { appId: string; patientId: string };

const patients = [
  { id: "p1", fornavn: "Ola", etternavn: "Nordmann", birthDate: "1990-01-01" },
  { id: "p2", fornavn: "Kari", etternavn: "Hansen", birthDate: "1985-05-05" },
].map((p) => ({
  ...p,
  personident: `0101901234${p.id.slice(1)}`,
  personidentType: "FNR",
  gender: "MALE",
}));

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

function fakeBackend() {
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
      if (key === "GET /api/helsepersonell/me") {
        return json(route, { hpr: "9144889", legekontorId: "k1", navn: "Kari Lege", autorisasjon: "Lege" });
      }
      if (key === "GET /api/legekontor/k1") {
        return json(route, { id: "k1", navn: "Storgata legekontor", orgnummer: "123456789", tlf: null });
      }
      if (key === "GET /api/patient") return json(route, patients);
      if (key === "GET /api/apps") return json(route, [app]);
      if (key === `GET /api/patient/${patientId}` && known) return json(route, known);
      if (key === `GET /api/patients/${patientId}/konsultasjoner` && known) {
        return json(route, [
          {
            id: `k-${known.id}`,
            pasientId: known.id,
            hpr: ["9144889"],
            journalnotat: [],
            diagnoser: [],
            startetTidspunkt: "2026-09-30T09:14:00",
            avsluttetTidspunkt: null,
            status: "PÅGÅENDE",
            problemstilling: null,
          },
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

test("a launch always carries the patient of the open journal", async ({ context, page }) => {
  const backend = fakeBackend();
  await backend.install(context);
  const nav = page.getByRole("navigation", { name: "Oppgaver" });
  const startSykmelding = () => nav.getByRole("button", { name: /Sykmelding/ }).click();

  await page.goto("/patients/p1");
  await expect(page.getByRole("tab", { name: "Journal · Ola Nordmann" })).toBeVisible();
  await startSykmelding();
  await expect(page.locator("iframe[title='Sykmelding (syk-inn) for Ola Nordmann']")).toBeVisible();
  expect(backend.state.launches).toEqual([{ appId: "syk-inn", patientId: "p1" }]);

  await nav.getByRole("button", { name: "Pasienter" }).click();
  await page.getByRole("button", { name: "Åpne journal for Kari Hansen" }).click();
  await page.getByRole("button", { name: "Lukk og bytt pasient" }).click();

  await expect(page.getByRole("tab", { name: "Journal · Kari Hansen" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Journal · Ola Nordmann" })).toHaveCount(0);
  await expect(page.getByRole("tab", { name: /Sykmelding/ })).toHaveCount(0);
  await expect(page.locator("iframe")).toHaveCount(0);
  expect(backend.state.activeId).toBe("p2");

  await startSykmelding();
  await expect(page.locator("iframe[title='Sykmelding (syk-inn) for Kari Hansen']")).toBeVisible();
  expect(backend.state.launches).toEqual([
    { appId: "syk-inn", patientId: "p1" },
    { appId: "syk-inn", patientId: "p2" },
  ]);
});

test("a launch cannot run for a patient other than the open journal", async ({ context, page }) => {
  const backend = fakeBackend();
  await backend.install(context);
  const nav = page.getByRole("navigation", { name: "Oppgaver" });

  await page.goto("/patients/p2");
  await expect(page.getByRole("tab", { name: "Journal · Kari Hansen" })).toBeVisible();
  await nav.getByRole("button", { name: /Sykmelding/ }).click();
  await expect(page.locator("iframe")).toBeVisible();

  const other = await context.newPage();
  await other.goto("/patients/p1");
  await expect(other.getByRole("tab", { name: "Journal · Ola Nordmann" })).toBeVisible();
  expect(backend.state.activeId).toBe("p1");

  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.locator("iframe")).toHaveCount(0);
  const overlay = page.locator(".xp-frame-over");
  await expect(
    overlay.getByRole("heading", {
      name: "Appen tilhørte Kari Hansen. Lukk eller start på nytt for Ola Nordmann.",
    }),
  ).toBeVisible();

  await overlay.getByRole("button", { name: "Lukk" }).click();
  await nav.getByRole("button", { name: /Sykmelding/ }).click();
  await expect(page.getByRole("alertdialog", { name: "Aktiv pasient er en annen enn journalen." })).toBeVisible();
  await expect(page.locator("iframe")).toHaveCount(0);
  expect(backend.state.launches).toEqual([{ appId: "syk-inn", patientId: "p2" }]);
});
