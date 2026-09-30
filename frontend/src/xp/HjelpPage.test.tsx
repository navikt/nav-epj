import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HjelpPage } from "./HjelpPage";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

describe("HjelpPage", () => {
  it("has the page heading and one section heading per topic", () => {
    render(<HjelpPage />);
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s13.title"] }),
    ).toBeInTheDocument();
    expect(
      screen
        .getAllByRole("heading", { level: 2 })
        .map((heading) => heading.textContent),
    ).toEqual([
      copy["s13.keys.title"],
      copy["s13.mode.title"],
      copy["s13.switch.title"],
      copy["s13.test.title"],
    ]);
  });

  it("lists every shortcut with its effect in a labelled table", () => {
    render(<HjelpPage />);
    const table = screen.getByRole("table", { name: copy["s13.keys.title"] });
    expect(
      within(table).getAllByRole("columnheader").map((h) => h.textContent),
    ).toEqual([copy["s13.keys.col.key"], copy["s13.keys.col.what"]]);
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(6);
    expect(within(rows[0]).getByText("Ctrl+Shift+P")).toBeInTheDocument();
    expect(within(rows[0]).getByText("Går til pasientsøket")).toBeInTheDocument();
    expect(
      within(rows[1]).getByText("Ctrl+W (eller Ctrl+Alt+W)"),
    ).toBeInTheDocument();
    expect(screen.getByText(copy["s13.keys.note"])).toBeInTheDocument();
  });

  it("explains both view modes and the one-hour access window", () => {
    render(<HjelpPage />);
    expect(screen.getByText(copy["s13.mode.iframe"])).toBeInTheDocument();
    expect(screen.getByText(copy["s13.mode.tab"])).toBeInTheDocument();
    expect(screen.getByText(copy["s13.switch.body"])).toBeInTheDocument();
    expect(screen.getByText(copy["s13.switch.warning"])).toBeInTheDocument();
    expect(screen.getByText(copy["s13.test.body"])).toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<HjelpPage />);
    await expectNoSeriousViolations(container);
  });
});
