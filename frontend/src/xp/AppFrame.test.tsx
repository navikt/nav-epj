import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppFrame, FRAME_TIMEOUT_MS } from "./AppFrame";
import { seedRun, launchOk } from "./appFixtures";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function setup(
  options: { launch?: boolean; stale?: boolean; status?: "error" | "session" } = {},
) {
  seedRun();
  if (options.launch !== false) {
    useAppRunStore.getState().setLaunchUrl("syk-inn", launchOk().body.launchUrl);
  }
  if (options.status) useAppRunStore.getState().setStatus("syk-inn", options.status);
  const handlers = {
    onRestart: vi.fn(),
    onPopOut: vi.fn(),
    onClose: vi.fn(),
    onOpenJournal: vi.fn(),
  };
  function Harness() {
    const run = useAppRunStore((s) => s.runs[0]);
    return (
      <AppFrame
        run={run}
        stale={options.stale ?? false}
        otherPatientName="Kari Hansen"
        {...handlers}
      />
    );
  }
  const view = render(<Harness />);
  return { ...view, ...handlers };
}

describe("AppFrame", () => {
  afterEach(() => vi.useRealTimers());

  it("renders a sandboxed iframe without top navigation rights", () => {
    setup();
    const frame = screen.getByTitle("Sykmelding (syk-inn) for Ola Nordmann");
    expect(frame).toHaveAttribute(
      "sandbox",
      "allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads",
    );
    expect(frame.getAttribute("sandbox")).not.toContain("allow-top-navigation");
    expect(frame).toHaveAttribute("referrerpolicy", "no-referrer");
    expect(frame).toHaveAttribute("allow", "");
    expect(frame).toHaveAttribute("src", expect.stringContaining("launch=id-1"));
  });

  it("shows the checking step before the launch url is known", () => {
    setup({ launch: false });
    expect(
      screen.getByRole("status", { name: "" }),
    ).toHaveTextContent(copy["s6.step1.busy"]);
    expect(document.querySelector("iframe")).toBeNull();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  it("shows the waiting step and the fallback hint while the frame loads", () => {
    setup();
    expect(screen.getByText(copy["s6.step2.wait"]("https://syk.example"), { exact: false })).toBeInTheDocument();
    expect(screen.getByText(copy["s6.hint"])).toBeInTheDocument();
    expect(screen.getByText(new RegExp(copy["s6.step1.ok"]("Ola Nordmann", "09:14")))).toBeInTheDocument();
  });

  it("switches to running and logs the load when the frame loads", () => {
    setup();
    fireEvent.load(document.querySelector("iframe") as HTMLIFrameElement);
    const [run] = useAppRunStore.getState().runs;
    expect(run.status).toBe("running");
    expect(run.events).toMatchObject([{ kind: "load", url: "https://syk.example" }]);
    expect(screen.queryByText(copy["s6.hint"])).not.toBeInTheDocument();
  });

  it("times out after 8 seconds without a load event", () => {
    vi.useFakeTimers();
    setup();
    act(() => {
      vi.advanceTimersByTime(FRAME_TIMEOUT_MS - 1);
    });
    expect(useAppRunStore.getState().runs[0].status).toBe("starting");
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(useAppRunStore.getState().runs[0].status).toBe("timeout");
    expect(useAppDialogStore.getState().dialog).toMatchObject({
      kind: "error",
      code: "FRAMING_REFUSED",
    });
    expect(screen.getByText(copy["s6.timeout.title"])).toBeInTheDocument();
  });

  it("does not time out once the frame has loaded", () => {
    vi.useFakeTimers();
    setup();
    fireEvent.load(document.querySelector("iframe") as HTMLIFrameElement);
    act(() => {
      vi.advanceTimersByTime(FRAME_TIMEOUT_MS * 2);
    });
    expect(useAppRunStore.getState().runs[0].status).toBe("running");
    expect(useAppDialogStore.getState().dialog).toBeNull();
  });

  it("recovers when the frame loads after the timeout", () => {
    vi.useFakeTimers();
    setup();
    act(() => {
      vi.advanceTimersByTime(FRAME_TIMEOUT_MS);
    });
    fireEvent.load(document.querySelector("iframe") as HTMLIFrameElement);
    expect(useAppRunStore.getState().runs[0].status).toBe("running");
  });

  it("closes the framing dialog when the frame loads after the timeout", () => {
    vi.useFakeTimers();
    setup();
    act(() => {
      vi.advanceTimersByTime(FRAME_TIMEOUT_MS);
    });
    expect(useAppDialogStore.getState().dialog).toMatchObject({
      code: "FRAMING_REFUSED",
    });
    fireEvent.load(document.querySelector("iframe") as HTMLIFrameElement);
    expect(useAppDialogStore.getState().dialog).toBeNull();
  });

  it("keeps unrelated dialogs open when the frame loads late", () => {
    vi.useFakeTimers();
    setup();
    act(() => {
      vi.advanceTimersByTime(FRAME_TIMEOUT_MS);
    });
    useAppDialogStore.getState().show({ kind: "tabApp", tabId: "x" });
    fireEvent.load(document.querySelector("iframe") as HTMLIFrameElement);
    expect(useAppDialogStore.getState().dialog).toEqual({ kind: "tabApp", tabId: "x" });
  });

  it("offers pop-out and close from the timeout state", async () => {
    const { onPopOut, onClose } = setup();
    act(() => useAppRunStore.getState().setStatus("syk-inn", "timeout"));
    await userEvent.click(screen.getByRole("button", { name: copy["s5.popout"] }));
    await userEvent.click(screen.getByRole("button", { name: copy["s5.close"] }));
    expect(onPopOut).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("hides the app behind the stale overlay without unmounting it", async () => {
    const { onOpenJournal, onClose } = setup({ stale: true });
    const frame = document.querySelector("iframe");
    expect(frame).not.toBeNull();
    expect(frame).toHaveAttribute("aria-hidden", "true");
    expect(frame).toHaveAttribute("inert");
    expect(
      screen.getByText(copy["s5.stale.title"]("Ola Nordmann", "Kari Hansen")),
    ).toBeInTheDocument();
    expect(screen.getByText(copy["s5.stale.body"])).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", { name: copy["s5.stale.open"]("Kari Hansen") }),
    );
    await userEvent.click(screen.getByRole("button", { name: copy["s5.close"] }));
    expect(onOpenJournal).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("shows the error state with restart, pop-out and close", async () => {
    const { onRestart } = setup({ status: "error" });
    expect(document.querySelector("iframe")).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent(copy["s5.error.title"]);
    await userEvent.click(
      screen.getByRole("button", { name: copy["s5.error.restart"] }),
    );
    expect(onRestart).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: copy["s5.popout"] })).toBeInTheDocument();
  });

  it("shows the session state and reloads the page", async () => {
    const reload = vi.fn();
    vi.stubGlobal("location", { ...window.location, reload });
    setup({ status: "session" });
    expect(screen.getByRole("alert")).toHaveTextContent(copy["s5.session.title"]);
    await userEvent.click(
      screen.getByRole("button", { name: copy["s5.session.reload"] }),
    );
    expect(reload).toHaveBeenCalledOnce();
    vi.unstubAllGlobals();
  });

  it.each([
    ["running", {}],
    ["starting", { launch: false }],
    ["stale", { stale: true }],
    ["error", { status: "error" as const }],
  ])("has no serious accessibility violations (%s)", async (_name, options) => {
    const { container } = setup(options);
    await expectNoSeriousViolations(container, { iframes: false });
  });
});
