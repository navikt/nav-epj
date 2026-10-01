import { test, type Page } from "@playwright/test";
import path from "node:path";
import { fakeBackend } from "./fakeBackend";

const dir = process.env.SCREENS_DIR ?? path.join("test-results", "screens");

const shot = (page: Page, name: string) =>
  page.screenshot({ path: path.join(dir, `${name}.png`), fullPage: true });

test.beforeEach(async ({ context, page }) => {
  await fakeBackend({ withHistory: true }).install(context);
  await page.clock.setFixedTime(new Date("2026-10-01T15:00:00"));
  await page.emulateMedia({ reducedMotion: "reduce" });
});

test("wide desktop", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/");
  await page.getByRole("navigation", { name: "Oppgaver" }).waitFor();
  await page.waitForLoadState("networkidle");
  await shot(page, "01-start");

  await page.getByRole("navigation", { name: "Oppgaver" }).getByRole("button", { name: "Pasienter" }).click();
  await page.getByRole("table").waitFor();
  await shot(page, "02-patients");

  await page.getByRole("button", { name: "Ny pasient" }).first().click();
  await page.getByRole("dialog").waitFor();
  await shot(page, "03-new-patient-dialog");
  await page.keyboard.press("Escape");

  await page.goto("/patients/p1");
  await page.getByRole("tab", { name: "Journal · Ola Nordmann" }).waitFor();
  await page.getByRole("tab", { name: /Konsultasjon/ }).first().waitFor();
  await page.waitForLoadState("networkidle");
  await shot(page, "04-journal-konsultasjon");

  await page.getByRole("combobox").click();
  await page.getByRole("option").first().waitFor();
  await shot(page, "05-journal-combobox-open");
  await page.keyboard.press("Escape");

  await page.getByRole("tab", { name: /Tidligere/ }).click();
  await shot(page, "06-journal-tidligere");

  await page.getByRole("tab", { name: /Apper/ }).last().click();
  await shot(page, "07-journal-apper");
});

test("narrow with drawer", async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 900 });
  await page.goto("/patients/p1");
  await page.getByRole("tab", { name: "Journal · Ola Nordmann" }).waitFor();
  await page.waitForLoadState("networkidle");
  await shot(page, "08-narrow-journal");
  await page.getByRole("button", { name: "Meny" }).click();
  await page.getByRole("navigation", { name: "Oppgaver" }).waitFor();
  await shot(page, "09-narrow-drawer");
});

test("klassisk theme", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.addInitScript(() => localStorage.setItem("nav-epj:theme", "klassisk"));
  await page.goto("/patients/p1");
  await page.getByRole("tab", { name: "Journal · Ola Nordmann" }).waitFor();
  await page.waitForLoadState("networkidle");
  await shot(page, "10-klassisk-journal");
  await page.getByRole("navigation", { name: "Oppgaver" }).getByRole("button", { name: "Pasienter" }).click();
  await page.getByRole("table").waitFor();
  await shot(page, "11-klassisk-patients");
});
