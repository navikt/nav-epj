import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { DiagnoseCombobox } from "./DiagnoseCombobox";
import { diagnoseKey, type DiagnoseItem } from "./journalStore";
import { expectNoSeriousViolations } from "./axeHelper";

const a02: DiagnoseItem = { code: "A02", system: "ICPC2", text: "Frysninger" };

function Harness({
  initial = [],
  saved = new Set<string>(),
  onRemoved,
}: {
  initial?: DiagnoseItem[];
  saved?: Set<string>;
  onRemoved?: (code: string) => void;
}) {
  const [selected, setSelected] = useState(initial);
  return (
    <DiagnoseCombobox
      selected={selected}
      savedKeys={saved}
      onAdd={(d) => setSelected((s) => [...s, d])}
      onRemove={(code, system) => {
        onRemoved?.(code);
        setSelected((s) => s.filter((d) => diagnoseKey(d) !== `${system}:${code}`));
      }}
    />
  );
}

const input = () => screen.getByRole("combobox");
const toggle = () =>
  screen.getByRole("button", { name: /diagnoseliste/ });

describe("DiagnoseCombobox", () => {
  it("exposes a labelled combobox with a description and a toggle button", async () => {
    const { container } = render(<Harness />);
    expect(input()).toHaveAccessibleName("Diagnoser (ICPC-2 eller ICD-10)");
    expect(input()).toHaveAccessibleDescription("Pil ned åpner listen. Enter velger.");
    expect(input()).toHaveAttribute("aria-expanded", "false");
    expect(toggle()).toHaveAccessibleName("Vis diagnoseliste");
    expect(toggle()).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByText("Ingen diagnoser valgt.")).toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("swaps the toggle label and chevron direction when opened and closed", async () => {
    render(<Harness />);
    const user = userEvent.setup();
    const path = () => toggle().querySelector("path")!.getAttribute("d");
    const closedPath = path();
    await user.click(toggle());
    expect(toggle()).toHaveAccessibleName("Skjul diagnoseliste");
    expect(toggle()).toHaveAttribute("aria-expanded", "true");
    expect(input()).toHaveAttribute("aria-expanded", "true");
    expect(path()).not.toBe(closedPath);
    await user.click(toggle());
    expect(toggle()).toHaveAccessibleName("Vis diagnoseliste");
    expect(path()).toBe(closedPath);
  });

  it("opens with ArrowDown, selects with Enter and shows a chip", async () => {
    render(<Harness />);
    const user = userEvent.setup();
    input().focus();
    await user.keyboard("{ArrowDown}");
    expect(input()).toHaveAttribute("aria-expanded", "true");
    expect(screen.getAllByRole("option")).toHaveLength(5);
    await user.keyboard("{ArrowDown}{Enter}");
    const chips = screen.getByRole("list", { name: "Valgte diagnoser" });
    expect(chips.querySelectorAll("li")).toHaveLength(1);
  });

  it("names the listbox, marks it multiselectable and passes axe while open", async () => {
    const { container } = render(<Harness />);
    await userEvent.click(toggle());
    const list = screen.getByRole("listbox", { name: "Diagnoser (ICPC-2 eller ICD-10)" });
    expect(list).toHaveAttribute("aria-multiselectable", "true");
    await expectNoSeriousViolations(container);
  });

  it("filters on code or description while typing", async () => {
    render(<Harness />);
    const user = userEvent.setup();
    await user.type(input(), "feber");
    const options = screen.getAllByRole("option");
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent("A03");
    await user.clear(input());
    await user.type(input(), "B0");
    expect(screen.getAllByRole("option")).toHaveLength(2);
  });

  it("shows no-hits text for an unknown search", async () => {
    render(<Harness />);
    await userEvent.type(input(), "zzz");
    expect(screen.getByText("Ingen treff")).toBeInTheDocument();
  });

  it("closes on Escape without letting the key reach outer handlers", async () => {
    const outer = vi.fn();
    render(
      <div onKeyDown={outer}>
        <Harness />
      </div>,
    );
    const user = userEvent.setup();
    input().focus();
    await user.keyboard("{ArrowDown}");
    expect(input()).toHaveAttribute("aria-expanded", "true");
    await user.keyboard("{Escape}");
    expect(input()).toHaveAttribute("aria-expanded", "false");
    expect(outer).not.toHaveBeenCalledWith(expect.objectContaining({ key: "Escape" }));
    await user.keyboard("{Escape}");
    expect(outer).toHaveBeenCalledWith(expect.objectContaining({ key: "Escape" }));
  });

  it("closes when focus leaves the combobox", async () => {
    render(
      <>
        <Harness />
        <button type="button">Utenfor</button>
      </>,
    );
    const user = userEvent.setup();
    await user.click(toggle());
    await user.click(screen.getByRole("button", { name: "Utenfor" }));
    expect(input()).toHaveAttribute("aria-expanded", "false");
  });

  it("renders chips with a lagret marker for saved diagnoses and removes any chip", async () => {
    const onRemoved = vi.fn();
    render(
      <Harness
        initial={[a02, { code: "A03", system: "ICPC2", text: "Feber" }]}
        saved={new Set([diagnoseKey(a02)])}
        onRemoved={onRemoved}
      />,
    );
    const saved = screen.getByText("Frysninger").closest("li")!;
    expect(saved).toHaveTextContent("· lagret");
    expect(screen.getByText("Feber").closest("li")).not.toHaveTextContent("· lagret");
    await userEvent.click(
      screen.getByRole("button", { name: "Fjern A02 Frysninger" }),
    );
    expect(onRemoved).toHaveBeenCalledWith("A02");
    expect(screen.queryByText("Frysninger")).toBeNull();
  });

  it("can be disabled", () => {
    const { rerender } = render(
      <DiagnoseCombobox selected={[a02]} savedKeys={new Set()} disabled onAdd={() => {}} onRemove={() => {}} />,
    );
    expect(input()).toBeDisabled();
    expect(toggle()).toBeDisabled();
    expect(screen.getByRole("button", { name: "Fjern A02 Frysninger" })).toBeDisabled();
    rerender(<div />);
  });
});
