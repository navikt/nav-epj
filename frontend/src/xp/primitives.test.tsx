import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { Badge } from "./Badge";
import { Card } from "./Card";
import { IconButton } from "./IconButton";
import { Note } from "./Note";
import { Progress } from "./Progress";
import { SegmentedControl } from "./SegmentedControl";
import { SubTabs } from "./SubTabs";
import { TextArea } from "./TextArea";
import { TextField } from "./TextField";
import { expectNoSeriousViolations } from "./axeHelper";

function Tabs() {
  const [selected, setSelected] = useState<"a" | "b" | "c">("a");
  return (
    <SubTabs
      label="Journal"
      idPrefix="j"
      tabs={[
        { id: "a", label: "Alfa" },
        { id: "b", label: "Beta" },
        { id: "c", label: "Gamma" },
      ]}
      selected={selected}
      onSelect={setSelected}
    >
      <p>innhold {selected}</p>
    </SubTabs>
  );
}

describe("IconButton", () => {
  it("exposes its label and fires clicks", async () => {
    const onClick = vi.fn();
    const { container } = render(
      <IconButton icon="journal" label="Åpne journal" onClick={onClick} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Åpne journal" }));
    expect(onClick).toHaveBeenCalledOnce();
    await expectNoSeriousViolations(container);
  });

  it("can be disabled", () => {
    render(<IconButton icon="journal" label="Åpne" disabled />);
    expect(screen.getByRole("button", { name: "Åpne" })).toBeDisabled();
  });
});

describe("Badge, Card, Note, Progress", () => {
  it("renders with tone classes and passes axe", async () => {
    const { container } = render(
      <>
        <Badge tone="ok">Klar</Badge>
        <Card heading="Tittel" headingId="t">
          <p>Innhold</p>
        </Card>
        <Note tone="warn" role="alert">
          Advarsel
        </Note>
        <Progress label="Lagrer" />
      </>,
    );
    expect(screen.getByText("Klar")).toHaveClass("xp-badge", "ok");
    expect(screen.getByRole("region", { name: "Tittel" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Advarsel");
    expect(screen.getByRole("progressbar", { name: "Lagrer" })).toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });
});

describe("TextField", () => {
  it("labels the input and links hint and error", async () => {
    const { container } = render(
      <TextField label="Fornavn" hint="Som i folkeregisteret" error="Mangler" />,
    );
    const input = screen.getByLabelText("Fornavn");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("Som i folkeregisteret ✖ Mangler");
    await expectNoSeriousViolations(container);
  });

  it("is not marked invalid without an error", () => {
    render(<TextField label="Fornavn" />);
    expect(screen.getByLabelText("Fornavn")).not.toHaveAttribute("aria-invalid");
  });
});

describe("TextArea", () => {
  it("labels the textarea and links the hint", async () => {
    const { container } = render(<TextArea label="Notat" hint="Fritekst" />);
    expect(screen.getByLabelText("Notat")).toHaveAccessibleDescription("Fritekst");
    await expectNoSeriousViolations(container);
  });
});

describe("SegmentedControl", () => {
  it("marks the active option and reports changes", async () => {
    const onChange = vi.fn();
    const { container } = render(
      <SegmentedControl
        label="Utvalg"
        value="a"
        options={[
          { value: "a", label: "Mine" },
          { value: "b", label: "Nylig" },
        ]}
        onChange={onChange}
      />,
    );
    expect(screen.getByRole("button", { name: "Mine" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await userEvent.click(screen.getByRole("button", { name: "Nylig" }));
    expect(onChange).toHaveBeenCalledWith("b");
    await expectNoSeriousViolations(container);
  });
});

describe("SubTabs", () => {
  it("links tabs and panel and passes axe", async () => {
    const { container } = render(<Tabs />);
    expect(screen.getByRole("tab", { name: "Alfa" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tabpanel", { name: "Alfa" })).toHaveTextContent(
      "innhold a",
    );
    await expectNoSeriousViolations(container);
  });

  it("uses roving tabindex and arrow keys with wrap-around", async () => {
    render(<Tabs />);
    const user = userEvent.setup();
    screen.getByRole("tab", { name: "Alfa" }).focus();
    expect(screen.getByRole("tab", { name: "Beta" })).toHaveAttribute("tabindex", "-1");
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Beta" })).toHaveFocus();
    expect(screen.getByRole("tabpanel")).toHaveTextContent("innhold b");
    await user.keyboard("{End}");
    expect(screen.getByRole("tab", { name: "Gamma" })).toHaveFocus();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Alfa" })).toHaveFocus();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("tab", { name: "Gamma" })).toHaveFocus();
    await user.keyboard("{Home}");
    expect(screen.getByRole("tab", { name: "Alfa" })).toHaveFocus();
  });
});
