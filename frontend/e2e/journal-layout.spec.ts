import { expect, test, type Locator } from "@playwright/test";
import { fakeBackend } from "./fakeBackend";

function edges(locator: Locator) {
  return locator.evaluate((element) => {
    const { left, right } = element.getBoundingClientRect();
    return { left, right };
  });
}

test.beforeEach(async ({ context, page }) => {
  await fakeBackend({ withHistory: true }).install(context);
  await page.emulateMedia({ reducedMotion: "reduce" });
});

for (const width of [1280, 800]) {
  test(`journal alignment and dialog insets at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/patients/p1");
    await expect(page.getByRole("heading", { name: "Ola Nordmann" })).toBeVisible();
    const banner = await edges(page.getByRole("region", { name: "Pasientkontekst" }));
    const tabs = page.getByRole("tablist", { name: "Journal", exact: true });
    const panel = page.locator("#journal-panel");

    for (const name of ["Konsultasjon", /^Tidligere konsultasjoner/, /^Apper/]) {
      await tabs.getByRole("tab", { name, exact: true }).click();
      await expect(panel.locator(":scope > :first-child")).toBeVisible();
      expect(await edges(tabs)).toEqual(banner);
      expect(await edges(panel)).toEqual(banner);
      expect(await edges(panel.locator(":scope > :first-child"))).toEqual(banner);
      expect((await edges(tabs.getByRole("tab").first())).left).toBe(banner.left);
    }

    if (width === 800) await page.getByRole("button", { name: "Meny", exact: true }).click();
    await page.getByRole("navigation", { name: "Oppgaver" })
      .getByRole("button", { name: "Kontrollpanel", exact: true }).click();
    await page.getByRole("button", { name: /^SMART-apper/ }).click();
    await page.getByRole("button", { name: "Egenskaper", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Egenskaper for Sykmelding" });
    const body = await edges(dialog.locator(".xp-body"));
    const dialogTabs = dialog.getByRole("tablist");
    expect((await edges(dialogTabs.getByRole("tab").first())).left).toBe(body.left + 8);
    for (const name of ["Generelt", "OAuth-klient", "Tilganger"]) {
      await dialogTabs.getByRole("tab", { name, exact: true }).click();
      expect(await edges(dialog.getByRole("tabpanel").locator(":scope > :first-child")))
        .toEqual({ left: body.left + 16, right: body.right - 16 });
    }
  });
}
