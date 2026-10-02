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

function gaps(container: Locator) {
  return container.evaluate((element) => {
    const boxes = [...element.children].map((child) => child.getBoundingClientRect());
    return boxes.slice(1).map((box, index) => box.top - boxes[index].bottom);
  });
}

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
});

for (const width of [1280, 800]) {
  test(`start page cards are the same size at ${width}px`, async ({ context, page }) => {
    await fakeBackend().install(context);
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    const cards = page.locator(".xp-card-grid > .xp-card");
    await expect(cards).toHaveCount(2);
    const [first, ...rest] = await sizes(cards);
    for (const size of rest) expect(size).toEqual(first);
  });

  test(`app card buttons share one baseline at ${width}px`, async ({ context, page }) => {
    await fakeBackend().install(context);
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/patients/p1?tab=apper");
    const starts = page.locator(".xp-appcards").getByRole("button", { name: "Start", exact: true });
    await expect(starts).toHaveCount(2);
    const [first, second] = await starts.evaluateAll((buttons) =>
      buttons.map((button) => button.getBoundingClientRect().bottom),
    );
    expect(second).toBe(first);
  });

  test(`journal tab blocks are 16px apart at ${width}px`, async ({ context, page }) => {
    await fakeBackend({ ongoing: false, withHistory: true }).install(context);
    await page.setViewportSize({ width, height: 900 });
    const panel = page.locator("#journal-panel");

    await page.goto("/patients/p1?tab=apper");
    await expect(panel.getByText("Start en konsultasjon først.")).toBeVisible();
    expect(await gaps(panel)).toEqual([16]);

    await page.goto("/patients/p1?tab=tidligere");
    await expect(panel.getByRole("table")).toBeVisible();
    expect(await gaps(panel)).toEqual([16]);
  });
}
