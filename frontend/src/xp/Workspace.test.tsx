import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { Workspace } from "./Workspace";
import { useWorkspaceStore } from "./workspaceStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

describe("Workspace", () => {
  beforeEach(() => act(() => useWorkspaceStore.getState().reset()));

  it("renders a main landmark with tabs and a tabpanel for the page", () => {
    render(
      <Workspace>
        <p>Sideinnhold</p>
      </Workspace>,
    );
    const main = screen.getByRole("main");
    expect(main).toContainElement(screen.getByRole("tablist"));
    const panel = screen.getByRole("tabpanel", { name: copy["tabs.start"] });
    expect(panel).toHaveAttribute("id", "work-panel");
    expect(panel).toHaveTextContent("Sideinnhold");
  });

  it("labels the panel by the current tab", () => {
    render(
      <Workspace>
        <p>Sideinnhold</p>
      </Workspace>,
    );
    act(() => {
      useWorkspaceStore.getState().openTab({ kind: "patients", label: "Pasienter" });
    });
    expect(screen.getByRole("tabpanel", { name: "Pasienter" })).toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(
      <Workspace>
        <h1>Tittel</h1>
      </Workspace>,
    );
    await expectNoSeriousViolations(container);
  });
});
