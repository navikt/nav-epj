import { axe } from "vitest-axe";
import { expect } from "vitest";

export async function expectNoSeriousViolations(container: Element) {
  const results = await axe(container);
  const serious = results.violations.filter(
    (v) => v.impact === "serious" || v.impact === "critical",
  );
  expect(serious.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
}
