import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useBalloonStore } from "./balloonStore";
import { BalloonHost } from "./BalloonHost";
import { expectNoSeriousViolations } from "./axeHelper";

const show = (title: string, body = "Tekst") =>
  act(() => useBalloonStore.getState().show({ title, body }));

describe("BalloonHost", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders nothing without a balloon", () => {
    const { container } = render(<BalloonHost />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a balloon as a status and passes axe", async () => {
    const { container } = render(<BalloonHost />);
    show("Pasient lagret", "Lagt til");
    expect(screen.getByRole("status")).toHaveTextContent("Pasient lagretLagt til");
    await expectNoSeriousViolations(container);
  });

  it("shows only the latest balloon", () => {
    render(<BalloonHost />);
    show("Første");
    show("Andre");
    expect(screen.getAllByRole("status")).toHaveLength(1);
    expect(screen.getByText("Andre")).toBeInTheDocument();
  });

  it("closes on the close button", async () => {
    render(<BalloonHost />);
    show("Første");
    await userEvent.click(screen.getByRole("button", { name: "Lukk varsel" }));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("hides itself after six seconds", () => {
    vi.useFakeTimers();
    render(<BalloonHost />);
    show("Første");
    act(() => vi.advanceTimersByTime(5900));
    expect(screen.getByRole("status")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(200));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("restarts the timer for a replacing balloon", () => {
    vi.useFakeTimers();
    render(<BalloonHost />);
    show("Første");
    act(() => vi.advanceTimersByTime(4000));
    show("Andre");
    act(() => vi.advanceTimersByTime(4000));
    expect(screen.getByText("Andre")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(2100));
    expect(screen.queryByRole("status")).toBeNull();
  });
});
