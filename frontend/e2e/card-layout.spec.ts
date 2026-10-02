import { expect, test, type Locator } from "@playwright/test";
import { fakeBackend } from "./fakeBackend";

function sizes(locator: Locator) {
  return locator.evaluateAll((elements) =>
    elements.map((element) => {
      const { width, height } = element.getBoundingClientRect();
      return { width, height };
    }),
  );
}

test.beforeEach(async ({ context, page }) => {
  await fakeBackend().install(context);
  await page.emulateMedia({ reducedMotion: "reduce" });
});

for (const width of [1280, 800]) {
  test(`start page cards are the same size at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    const cards = page.locator(".xp-card-grid > .xp-card");
    await expect(cards).toHaveCount(2);
    const [first, ...rest] = await sizes(cards);
    for (const size of rest) expect(size).toEqual(first);
  });

  test(`app card buttons share one baseline at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/patients/p1?tab=apper");
    const starts = page.locator(".xp-appcards").getByRole("button", { name: "Start", exact: true });
    await expect(starts).toHaveCount(2);
    const [first, second] = await starts.evaluateAll((buttons) =>
      buttons.map((button) => button.getBoundingClientRect().bottom),
    );
    expect(second).toBe(first);
  });
}
