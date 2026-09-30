import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { XpIcon } from "./XpIcon";
import { expectNoSeriousViolations } from "./axeHelper";

describe("XpIcon", () => {
  it("renders a decorative image with the requested size", () => {
    const { container } = render(<XpIcon name="pasient" size={32} />);
    const img = container.querySelector("img")!;
    expect(img).toHaveAttribute("alt", "");
    expect(img).toHaveAttribute("aria-hidden", "true");
    expect(img).toHaveAttribute("width", "32");
    expect(img.getAttribute("src")).toBeTruthy();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<XpIcon name="hjem" />);
    await expectNoSeriousViolations(container);
  });
});
