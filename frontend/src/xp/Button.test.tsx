import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "./Button";
import { expectNoSeriousViolations } from "./axeHelper";

describe("Button", () => {
  it("renders a button that does not submit forms by default", () => {
    render(<Button>Åpne</Button>);
    expect(screen.getByRole("button", { name: "Åpne" })).toHaveAttribute(
      "type",
      "button",
    );
  });

  it("marks the default action and supports variants", () => {
    render(
      <>
        <Button isDefault>Ja</Button>
        <Button variant="small">Nei</Button>
      </>,
    );
    expect(screen.getByRole("button", { name: "Ja" })).toHaveClass(
      "xp-btn",
      "is-default",
    );
    expect(screen.getByRole("button", { name: "Nei" })).toHaveClass("small");
  });

  it("activates with click and keyboard", async () => {
    const onClick = vi.fn();
    const user = userEvent.setup();
    render(<Button onClick={onClick}>Klikk</Button>);
    await user.click(screen.getByRole("button"));
    await user.keyboard("{Enter}");
    expect(onClick).toHaveBeenCalledTimes(2);
  });

  it("does not fire when disabled", async () => {
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Klikk
      </Button>,
    );
    await userEvent.click(screen.getByRole("button"));
    expect(onClick).not.toHaveBeenCalled();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = render(<Button>Åpne</Button>);
    await expectNoSeriousViolations(container);
  });
});
