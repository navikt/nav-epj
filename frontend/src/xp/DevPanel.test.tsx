import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DevPanel } from "./DevPanel";
import { launchOk, seedRun } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { useBalloonStore } from "./balloonStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";

function setup(withLaunch = true) {
  seedRun();
  if (withLaunch) {
    useAppRunStore.getState().setLaunchUrl("syk-inn", launchOk().body.launchUrl);
    useAppRunStore.getState().addEvent("syk-inn", { kind: "launch", status: 200 });
    useAppRunStore.getState().addEvent("syk-inn", {
      kind: "load",
      url: "https://syk.example",
    });
  }
  const onClose = vi.fn();
  const run = useAppRunStore.getState().runs[0];
  const view = render(<DevPanel run={run} onClose={onClose} />);
  return { ...view, onClose };
}

describe("DevPanel", () => {
  it("is a labelled section with the host-side facts", () => {
    setup();
    const panel = screen.getByRole("region", {
      name: copy["dev.title"]("Sykmelding"),
    });
    expect(panel).toHaveAttribute("id", "dev-panel-syk-inn");
    const facts = within(panel);
    expect(facts.getByText("syk-inn")).toBeInTheDocument();
    expect(facts.getByText(launchOk().body.launchUrl)).toBeInTheDocument();
    expect(facts.getByText(copy["dev.launchId.value"]("id-1"))).toBeInTheDocument();
    expect(facts.getByText("https://epj.example/fhir")).toBeInTheDocument();
    expect(facts.getByText("Patient/p1")).toBeInTheDocument();
    expect(facts.getByText("Encounter/k1")).toBeInTheDocument();
    expect(facts.getByText("09:14")).toBeInTheDocument();
    expect(facts.getByText("10:14")).toBeInTheDocument();
    expect(facts.getByText("no-referrer")).toBeInTheDocument();
    expect(
      facts.getByText(/allow-scripts allow-same-origin allow-forms/),
    ).toBeInTheDocument();
  });

  it("lists launch results and frame loads with times", () => {
    setup();
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent(/\d\d:\d\d:\d\d\s+POST \/api\/launch → 200/);
    expect(items[1]).toHaveTextContent(/iframe lastet https:\/\/syk\.example/);
  });

  it("explains how to reach the console of the frame", () => {
    setup();
    expect(
      screen.getByText(copy["dev.devtoolsNote"]("https://syk.example")),
    ).toBeInTheDocument();
  });

  it("copies the facts without any token or secret", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    setup();
    await userEvent.click(screen.getByRole("button", { name: copy["dev.copy"] }));
    const text = writeText.mock.calls[0][0] as string;
    expect(text).toContain("client_id: syk-inn");
    expect(text).not.toMatch(/secret|access_token|bearer|01019012345/i);
    expect(useBalloonStore.getState().balloon?.title).toBe(copy["dev.copied"]);
    vi.unstubAllGlobals();
  });

  it("does not show the balloon when the clipboard is unavailable", async () => {
    vi.stubGlobal("navigator", {
      clipboard: { writeText: vi.fn().mockRejectedValue(new Error("denied")) },
    });
    setup();
    await userEvent.click(screen.getByRole("button", { name: copy["dev.copy"] }));
    expect(useBalloonStore.getState().balloon).toBeNull();
    vi.unstubAllGlobals();
  });

  it("closes with an accessible label", async () => {
    const { onClose } = setup();
    await userEvent.click(
      screen.getByRole("button", { name: copy["dev.close"] }),
    );
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("shows placeholders before the launch url is known", () => {
    setup(false);
    expect(screen.getAllByText("–").length).toBeGreaterThanOrEqual(3);
  });

  it("has no serious accessibility violations", async () => {
    const { container } = setup();
    await expectNoSeriousViolations(container);
  });
});
