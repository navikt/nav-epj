import { axe } from "vitest-axe";
import { expect } from "vitest";

export async function expectNoSeriousViolations(
  container: Element,
  options?: Parameters<typeof axe>[1],
) {
  const results = await axe(container, options);
  const serious = results.violations.filter(
    (v) => v.impact === "serious" || v.impact === "critical",
  );
  expect(serious.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}
