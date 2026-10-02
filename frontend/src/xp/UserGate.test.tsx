import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UserGate } from "./UserGate";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import type { HelsepersonellState } from "./useHelsepersonell";

const ready: HelsepersonellState = {
  status: "ready",
  helsepersonell: {
    hpr: "1",
    legekontorId: "k",
    navn: "Kari",
    autorisasjon: "Lege",
  },
  legekontor: { id: "k", navn: "Kontor" },
};

describe("UserGate", () => {
  it("announces loading and hides children", async () => {
    const { container } = render(
      <UserGate state={{ status: "loading" }} onRetry={() => {}}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(copy["s1.loading"]);
    expect(screen.queryByText("Innhold")).not.toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("shows an alert with retry on error", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const { container } = render(
      <UserGate state={{ status: "error" }} onRetry={onRetry}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      copy["s1.error.title"],
    );
    expect(screen.getByText(copy["s1.error.body"])).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: copy["s1.error.retry"] }),
    );
    expect(onRetry).toHaveBeenCalledOnce();
    await expectNoSeriousViolations(container);
  });

  it("renders children when ready", () => {
    render(
      <UserGate state={ready} onRetry={() => {}}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByText("Innhold")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
