import assert from "node:assert/strict";
import { test } from "node:test";
import { createHash } from "node:crypto";
import { buildApp } from "../src/app.ts";
import * as api from "../src/reference.ts";
import type { Steps } from "../src/steps.ts";
import { fixture } from "./fixture.ts";
import { checkDiscovery, checkIdentity } from "./contracts.ts";
import { age } from "../src/http.ts";

  test("Reference launch: protocol functions", async t => {
    const f = await fixture();
    t.after(f.close);
    const launch = { iss: f.config.fhirBase, launch: "one-time-launch" };
    const proof = api.pkce();
    const transaction = api.newTransaction(launch, f.metadata, proof);
    const tokens = await f.tokens();
    const context = api.launchContext(tokens, "h1");
    const cases: [string, () => unknown][] = [
      ["trusted launch parameters", () => {
        assert.deepEqual(api.readLaunch(launch, f.config), launch);
        assert.deepEqual(api.readLaunch({ ...launch, iss: `${launch.iss}/` }, f.config), launch);
        assert.throws(() => api.readLaunch({ ...launch, iss: `${launch.iss}//` }, f.config));
        assert.throws(() => api.readLaunch({ ...launch, iss: "https://untrusted.invalid/fhir" }, f.config));
      }],
      ["trust discovery and reject invalid metadata", () => checkDiscovery(f)],
      ["S256 PKCE", () => {
        const actual = api.pkce();
        assert.match(actual.verifier, /^[A-Za-z0-9_-]{43,128}$/);
        assert.equal(actual.challenge, createHash("sha256").update(actual.verifier).digest("base64url"));
        assert.notEqual(actual.verifier, api.pkce().verifier);
      }],
      ["server-side transaction and state", () => {
        const actual = api.newTransaction(launch, f.metadata, proof);
        assert.match(actual.state, /^[A-Za-z0-9_-]{43,}$/);
        assert.notEqual(actual.state, api.newTransaction(launch, f.metadata, proof).state);
        assert.equal(actual.verifier, proof.verifier);
        assert.equal(actual.launch.iss, launch.iss);
        assert.ok(Math.abs(Date.now() - actual.createdAt) < 1000);
      }],
      ["authorization request", () => {
        const url = new URL(api.authorizationUrl(f.config, transaction));
        for (const [key, value] of Object.entries({
          response_type: "code", client_id: f.config.clientId, redirect_uri: f.config.redirectUri,
          scope: f.config.scopes, aud: launch.iss, launch: launch.launch, state: transaction.state,
          code_challenge: proof.challenge, code_challenge_method: "S256",
        })) assert.equal(url.searchParams.get(key), value);
        assert.equal(url.searchParams.has("code_verifier"), false);
        const slashed = api.readLaunch({ ...launch, iss: `${launch.iss}/` }, f.config);
        const canonical = new URL(api.authorizationUrl(f.config, api.newTransaction(slashed, f.metadata, proof)));
        assert.equal(canonical.searchParams.get("aud"), f.config.fhirBase);
      }],
      ["callback state", () => {
        assert.equal(api.validateCallback({ code: "code", state: transaction.state }, transaction), "code");
        assert.throws(() => api.validateCallback({ code: "code", state: "tampered" }, transaction));
        assert.throws(() => api.validateCallback({ code: "code" }, transaction));
        assert.throws(() => api.validateCallback({ code: "code", state: transaction.state },
          { ...transaction, createdAt: Date.now() - 301_000 }));
      }],
      ["authenticated code exchange", async () => {
        f.registerCode("code", proof.challenge);
        const exchanged = await api.exchange(f.config, transaction, "code");
        assert.ok(f.issued.has(exchanged.access_token), "Exchange must return a token issued by the server");
        assert.equal(f.lastBody().get("grant_type"), "authorization_code");
        if (f.config.variant === "public") {
          assert.equal(f.lastAuthorization(), undefined);
          assert.equal(f.lastBody().has("client_assertion"), false);
        }
      }],
      ["verify ID-token signature and claims", () => checkIdentity(f)],
      ["launch context", () => {
        assert.deepEqual(api.launchContext(tokens, "h1"), context);
        assert.throws(() => api.launchContext({ ...tokens, patient: undefined }, "h1"));
        assert.throws(() => api.launchContext({ ...tokens, encounter: undefined }, "h1"));
      }],
      ["patient and consultation", async () => {
        const chart = await api.loadChart(f.config, tokens, context);
        assert.equal(chart.patientName, "Synthetic <Patient>");
        assert.equal(chart.encounter, "e1");
        assert.equal("clinician" in chart, false);
        assert.equal("organization" in chart, false);
        assert.equal("practitionerRoles" in chart, false);
        assert.equal(chart.age, age(f.patient.birthDate));
        f.encounter.subject.reference = "Patient/p2";
        await assert.rejects(() => api.loadChart(f.config, tokens, context), "Do not display another patient's encounter");
        f.encounter.subject.reference = "Patient/p1";
        f.encounter.status = "finished";
        await assert.rejects(() => api.loadChart(f.config, tokens, context), "Do not display a finished encounter as active");
      }],
    ];
    for (const [name, run] of cases) await t.test(name, async () => { await run(); });
  });

test("Reference launch: complete browser launch and escaped patient page", async t => {
  const f = await fixture();
  t.after(f.close);
  const app = await buildApp(f.config, true);
  t.after(() => app.close());
  const launch = await app.inject(`/launch?${new URLSearchParams({ iss: f.config.fhirBase, launch: "launch" })}`);
  assert.equal(launch.statusCode, 302);
  const url = new URL(launch.headers.location!);
  const cookie = launch.cookies.find(c => c.name === "smart_launch")!;
  assert.ok(cookie.httpOnly);
  assert.equal(cookie.sameSite, "Lax");
  f.registerCode("browser-code", url.searchParams.get("code_challenge")!);
  const callbackUrl = `/callback?${new URLSearchParams({ state: url.searchParams.get("state")!, code: "browser-code" })}`;
  assert.equal((await app.inject(callbackUrl)).statusCode, 400);
  assert.equal(f.tokenCalls(), 0);
  const callback = await app.inject({ url: callbackUrl, cookies: { smart_launch: cookie.value } });
  assert.equal(callback.statusCode, 302);
  assert.equal(callback.headers.location, "/app");
  assert.equal((await app.inject({ url: callbackUrl, cookies: { smart_launch: cookie.value } })).statusCode, 400);
  const session = callback.cookies.find(c => c.name === "smart_session")!;
  const chart = await app.inject({ url: "/app", cookies: { smart_session: session.value } });
  assert.equal(chart.statusCode, 200);
  assert.match(chart.body, /Synthetic &lt;Patient&gt;/);
  assert.doesNotMatch(chart.body, /Test clinician|Test practice|practitionerRoles/);
  assert.deepEqual(f.requests.filter(r => r.path.startsWith("/fhir/") && !r.path.includes(".well-known"))
    .map(r => r.path).sort(), ["/fhir/Encounter", "/fhir/Patient/p1"]);
  assert.equal(chart.headers["cache-control"], "no-store");
  const browserOutput = chart.body + JSON.stringify(callback.headers);
  for (const token of f.issued.keys()) assert.equal(browserOutput.includes(token), false);
  assert.doesNotMatch(browserOutput, /id_token|code_verifier/);
});

test("Reference launch: care-team reads are opt-in and require their permissions", async t => {
  const f = await fixture();
  t.after(f.close);
  const context = { patient: "p1", encounter: "e1", practitioner: "h1" };
  const tokens = await f.tokens({
    scope: `${f.config.scopes} patient/PractitionerRole.s patient/Organization.r`,
  });
  const basic = await api.loadChart(f.config, tokens, context);
  assert.deepEqual(Object.keys(basic).sort(), ["age", "encounter", "patientName", "started", "status"]);
  assert.deepEqual(f.requests.map(r => r.path).sort(), ["/fhir/Encounter", "/fhir/Patient/p1"],
    "Granting extra permissions must not enable extra reads");
  const detailed = await api.loadChart(f.config, tokens, context, { includeCareTeam: true });
  assert.deepEqual(detailed, {
    ...basic, clinician: "Test clinician", organization: "Test practice", practitionerRoles: 1,
  });
  await assert.rejects(() => api.loadChart(f.config, tokens, { ...context, encounter: "unknown" }),
    /not an active consultation/);
  await assert.rejects(() => api.loadChart(f.config, tokens, { ...context, encounter: "unknown" }, { includeCareTeam: true }),
    /not an active consultation/);
  const narrow = await f.tokens();
  await assert.rejects(() => api.loadChart(f.config, narrow, context, { includeCareTeam: true }),
    /HTTP 403/, "Opting in must not silently swallow missing permissions");
});

test("Reference launch: a missing organization does not block the basic chart", async t => {
  const f = await fixture();
  t.after(f.close);
  f.encounter.serviceProvider = undefined;
  const tokens = await f.tokens();
  const context = { patient: "p1", encounter: "e1", practitioner: "h1" };
  assert.equal((await api.loadChart(f.config, tokens, context)).encounter, "e1");
  await assert.rejects(() => api.loadChart(f.config, tokens, context, { includeCareTeam: true }), /serviceProvider/);
});

test("Reference launch: the existing renderer displays explicitly enabled care-team details", async t => {
  const f = await fixture();
  t.after(f.close);
  const tokens = await f.tokens({
    scope: `${f.config.scopes} patient/PractitionerRole.s patient/Organization.r`,
  });
  const implementation: Steps = {
    ...api,
    exchange: async () => tokens,
    loadChart: (config, sessionTokens, context) => api.loadChart(config, sessionTokens, context, { includeCareTeam: true }),
  };
  const app = await buildApp(f.config, true, implementation);
  t.after(() => app.close());
  const launch = await app.inject(`/launch?${new URLSearchParams({ iss: f.config.fhirBase, launch: "launch" })}`);
  const state = new URL(launch.headers.location!).searchParams.get("state")!;
  const callback = await app.inject({
    url: `/callback?${new URLSearchParams({ state, code: "code" })}`,
    cookies: { smart_launch: launch.cookies.find(c => c.name === "smart_launch")!.value },
  });
  assert.equal(callback.statusCode, 302);
  const chart = await app.inject({
    url: "/app", cookies: { smart_session: callback.cookies.find(c => c.name === "smart_session")!.value },
  });
  assert.equal(chart.statusCode, 200);
  assert.match(chart.body, /Test clinician/);
  assert.match(chart.body, /Test practice/);
  assert.match(chart.body, /practitionerRoles/);
});

test("Reference launch: tampered state never reaches the token endpoint", async t => {
  const f = await fixture();
  t.after(f.close);
  const app = await buildApp(f.config, true);
  t.after(() => app.close());
  const launch = await app.inject(`/launch?${new URLSearchParams({ iss: f.config.fhirBase, launch: "launch" })}`);
  assert.equal(launch.statusCode, 302, launch.body);
  const result = await app.inject({
    url: "/callback?state=wrong&code=stolen",
    cookies: { smart_launch: launch.cookies.find(c => c.name === "smart_launch")!.value },
  });
  assert.equal(result.statusCode, 400);
  assert.equal(f.tokenCalls(), 0);
});

test("Reference launch: expired app session requires a new launch", async t => {
  const f = await fixture();
  t.after(f.close);
  const implementation: Steps = { ...api, exchange: async () => ({ ...await f.tokens(), expires_in: -1 }) };
  const app = await buildApp(f.config, true, implementation);
  t.after(() => app.close());
  const launch = await app.inject(`/launch?${new URLSearchParams({ iss: f.config.fhirBase, launch: "launch" })}`);
  assert.equal(launch.statusCode, 302, launch.body);
  const state = new URL(launch.headers.location!).searchParams.get("state")!;
  const callback = await app.inject({
    url: `/callback?${new URLSearchParams({ state, code: "code" })}`,
    cookies: { smart_launch: launch.cookies.find(c => c.name === "smart_launch")!.value },
  });
  assert.equal(callback.statusCode, 302, callback.body);
  const session = callback.cookies.find(c => c.name === "smart_session")!;
  assert.equal((await app.inject({ url: "/app", cookies: { smart_session: session.value } })).statusCode, 401);
});
