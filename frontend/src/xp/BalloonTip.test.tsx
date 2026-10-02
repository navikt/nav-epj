import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BalloonTip } from "./BalloonTip";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function renderBalloon(onClose = vi.fn(), duration?: number) {
  return {
    onClose,
    ...render(
      <BalloonTip title="Konsultasjon lagret" duration={duration} onClose={onClose}>
        Journalnotatet er lagret.
      </BalloonTip>,
    ),
  };
}

describe("BalloonTip", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("renders title and text in a status region", () => {
    renderBalloon();
    const balloon = screen.getByRole("status");
    expect(balloon).toHaveTextContent("Konsultasjon lagret");
    expect(balloon).toHaveTextContent("Journalnotatet er lagret.");
    expect(screen.getByText("Konsultasjon lagret").tagName).toBe("B");
  });

  it("has a close button labelled Lukk varsel", () => {
    const { onClose } = renderBalloon();
    fireEvent.click(screen.getByRole("button", { name: copy["balloon.close"] }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("hides after 6 seconds", () => {
    const { onClose } = renderBalloon();
    act(() => {
      vi.advanceTimersByTime(5999);
    });
    expect(onClose).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("supports a custom duration", () => {
    const { onClose } = renderBalloon(vi.fn(), 1000);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("stays while hovered and restarts the timer after the pointer leaves", () => {
    const { onClose } = renderBalloon();
    const balloon = screen.getByRole("status");
    fireEvent.mouseEnter(balloon);
    act(() => {
      vi.advanceTimersByTime(20000);
    });
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.mouseLeave(balloon);
    act(() => {
      vi.advanceTimersByTime(5999);
    });
    expect(onClose).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("stays while focus is inside and hides after focus leaves", () => {
    const { onClose } = renderBalloon();
    const button = screen.getByRole("button");
    act(() => button.focus());
    act(() => {
      vi.advanceTimersByTime(20000);
    });
    expect(onClose).not.toHaveBeenCalled();
    act(() => button.blur());
    act(() => {
      vi.advanceTimersByTime(6000);
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("does not fire after unmount", () => {
    const { onClose, unmount } = renderBalloon();
    unmount();
    act(() => {
      vi.advanceTimersByTime(10000);
    });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("closes from the keyboard", async () => {
    vi.useRealTimers();
    const user = userEvent.setup();
    const { onClose } = renderBalloon();
    await user.tab();
    await user.keyboard("{Enter}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations", async () => {
    vi.useRealTimers();
    const { container } = renderBalloon();
    await expectNoSeriousViolations(container);
  });
});
