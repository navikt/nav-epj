import { expect, test } from "@playwright/test";
import { fakeBackend } from "./fakeBackend";

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

  const frame = page.locator("iframe");
  await frame.evaluate((node) => node.setAttribute("data-marker", "same"));
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(frame).toHaveAttribute("inert", "");
  const overlay = page.locator(".xp-frame-over");
  await expect(
    overlay.getByRole("heading", {
      name: "Appen tilhørte Kari Hansen. Lukk eller start på nytt for Ola Nordmann.",
    }),
  ).toBeVisible();

  expect(backend.state.launches).toHaveLength(1);

  await other.evaluate(() => fetch("/api/active-patient", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ patientId: "p2" }),
  }));
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(frame).not.toHaveAttribute("inert");
  await expect(frame).toHaveAttribute("data-marker", "same");
  expect(backend.state.launches).toHaveLength(1);

  await other.evaluate(() => fetch("/api/active-patient", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ patientId: "p1" }),
  }));
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(frame).toHaveAttribute("inert", "");
  await overlay.getByRole("button", { name: "Lukk" }).click();
  await nav.getByRole("button", { name: /Sykmelding/ }).click();
  await expect(page.getByRole("alertdialog", { name: "Aktiv pasient er en annen enn journalen." })).toBeVisible();
  await expect(page.locator("iframe")).toHaveCount(0);
  expect(backend.state.launches).toEqual([{ appId: "syk-inn", patientId: "p2" }]);
});
