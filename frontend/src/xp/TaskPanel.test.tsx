import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TaskPanel } from "./TaskPanel";
import { expectNoSeriousViolations } from "./axeHelper";

describe("TaskPanel", () => {
  it("renders a region labelled by its level 2 heading", () => {
    render(
      <TaskPanel title="System">
        <p>Innhold</p>
      </TaskPanel>,
    );
    const region = screen.getByRole("region", { name: "System" });
    expect(screen.getByRole("heading", { level: 2, name: "System" })).toBeInTheDocument();
    expect(region).toHaveTextContent("Innhold");
    expect(region).not.toHaveClass("primary");
  });

  it("marks the primary panel", () => {
    render(
      <TaskPanel title="Pasient" primary>
        <p>Innhold</p>
      </TaskPanel>,
    );
    expect(screen.getByRole("region", { name: "Pasient" })).toHaveClass("primary");
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(
      <TaskPanel title="System" primary>
        <p>Innhold</p>
      </TaskPanel>,
    );
    await expectNoSeriousViolations(container);
  });
});
