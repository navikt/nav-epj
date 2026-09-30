import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TestMarker } from "./TestMarker";
import { copy } from "./copy";
import { expectNoSeriousViolations } from "./axeHelper";

describe("TestMarker", () => {
  it("shows TEST with the tooltip text as title and for screen readers", () => {
    render(<TestMarker />);
    const marker = screen.getByTitle(copy["app.testTooltip"]);
    expect(marker).toHaveTextContent(copy["app.test"]);
    expect(marker).toHaveTextContent(copy["app.testTooltip"]);
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<TestMarker />);
    await expectNoSeriousViolations(container);
  });
});
