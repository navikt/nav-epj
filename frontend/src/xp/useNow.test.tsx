import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useNow } from "./useNow";

let renders = 0;

function Clock({ id }: { id: string }) {
  renders += 1;
  return <span data-testid={id}>{useNow().toISOString()}</span>;
}

describe("useNow", () => {
  beforeEach(() => {
    renders = 0;
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"] });
    vi.setSystemTime(new Date("2026-09-30T09:14:40Z"));
  });

  afterEach(() => vi.useRealTimers());

  it("reports the start of the current minute", () => {
    render(<Clock id="a" />);
    expect(screen.getByTestId("a")).toHaveTextContent("2026-09-30T09:14:00.000Z");
  });

  it("does not update before the minute changes", () => {
    render(<Clock id="a" />);
    act(() => {
      vi.advanceTimersByTime(19_000);
    });
    expect(screen.getByTestId("a")).toHaveTextContent("2026-09-30T09:14:00.000Z");
  });

  it("updates every consumer together on the minute", () => {
    render(
      <>
        <Clock id="a" />
        <Clock id="b" />
      </>,
    );
    act(() => {
      vi.advanceTimersByTime(20_000);
    });
    expect(screen.getByTestId("a")).toHaveTextContent("2026-09-30T09:15:00.000Z");
    expect(screen.getByTestId("b")).toHaveTextContent("2026-09-30T09:15:00.000Z");
    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    expect(screen.getByTestId("a")).toHaveTextContent("2026-09-30T09:16:00.000Z");
  });

  it("keeps no timer running without consumers", () => {
    const { unmount } = render(<Clock id="a" />);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("shows the current time to a consumer mounted later", () => {
    const first = render(<Clock id="a" />);
    first.unmount();
    vi.setSystemTime(new Date("2026-09-30T10:30:05Z"));
    render(<Clock id="b" />);
    expect(screen.getByTestId("b")).toHaveTextContent("2026-09-30T10:30:00.000Z");
  });
});
