import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { StatusBar } from "./StatusBar";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

describe("StatusBar", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
    vi.setSystemTime(new Date(2026, 8, 30, 9, 14, 58));
  });

  afterEach(() => vi.useRealTimers());

  it("is a labelled contentinfo landmark", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(
      screen.getByRole("contentinfo", { name: copy["status.label"] }),
    ).toBeInTheDocument();
  });

  it("shows the ready status in a status region", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent(copy["status.ready"]);
  });

  it("shows no active patient and opens the patient list", async () => {
    vi.useRealTimers();
    const onOpenPatients = vi.fn();
    render(<StatusBar onOpenPatients={onOpenPatients} />);
    const button = screen.getByRole("button", {
      name: copy["status.noPatient.aria"],
    });
    expect(button).toHaveTextContent(copy["status.noPatient"]);
    await userEvent.click(button);
    expect(onOpenPatients).toHaveBeenCalledOnce();
  });

  it("shows the TEST marker", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(
      within(screen.getByRole("contentinfo")).getByTitle(copy["app.testTooltip"]),
    ).toHaveTextContent(copy["app.test"]);
  });

  it("shows the clock and gives screen readers the full sentence", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.getByText("09:14")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText(copy["status.clock.sr"]("09:14"))).toBeInTheDocument();
  });

  it("updates the clock every second", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(screen.getByText("09:15")).toBeInTheDocument();
    expect(screen.getByText(copy["status.clock.sr"]("09:15"))).toBeInTheDocument();
  });

  it("does not render consultation or tab app segments yet", () => {
    render(<StatusBar onOpenPatients={vi.fn()} />);
    expect(screen.queryByText(/Konsultasjon/)).not.toBeInTheDocument();
    expect(screen.queryByText(/egen fane/)).not.toBeInTheDocument();
  });

  it("has no serious accessibility violations", async () => {
    vi.useRealTimers();
    const { container } = render(<StatusBar onOpenPatients={vi.fn()} />);
    await expectNoSeriousViolations(container);
  });
});
