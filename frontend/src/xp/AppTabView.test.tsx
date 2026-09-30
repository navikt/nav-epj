import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AppTabView } from "./AppTabView";
import { kari, launchOk, seedApps, seedJournal, seedRun } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { ShellContext } from "./shellContext";
import { useWorkspaceStore } from "./workspaceStore";

const navigate = vi.fn();

vi.mock("@tanstack/react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@tanstack/react-router")>()),
  useNavigate: () => navigate,
}));

function setup() {
  const announce = vi.fn();
  const view = render(
    <ShellContext.Provider
      value={{
        narrow: false,
        drawerOpen: false,
        setDrawerOpen: () => {},
        announce,
        rootElement: null,
      }}
    >
      <AppTabView clientId="syk-inn" />
    </ShellContext.Provider>,
  );
  return { ...view, announce };
}

describe("AppTabView", () => {
  beforeEach(() => {
    navigate.mockReset();
    seedApps();
    seedJournal();
    seedRun();
    useWorkspaceStore.getState().openTab({
      kind: "app",
      clientId: "syk-inn",
      label: "Sykmelding · ON",
    });
    useAppRunStore.getState().setLaunchUrl("syk-inn", launchOk().body.launchUrl);
  });

  it("shows the patient context, toolbar and frame for the run's own patient", () => {
    setup();
    const context = screen.getByRole("region", { name: copy["context.label"] });
    expect(context).toHaveTextContent("Ola Nordmann");
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: copy["s5.title"]("Sykmelding", "Ola Nordmann", "30.09 09:14"),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByTitle("Sykmelding (syk-inn) for Ola Nordmann"),
    ).toBeInTheDocument();
  });

  it("renders nothing for an unknown run", () => {
    useAppRunStore.getState().removeRun("syk-inn");
    const { container } = setup();
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the running footer with the access expiry and announces it", () => {
    const { announce } = setup();
    expect(screen.getByText(copy["s5.status.starting"])).toBeInTheDocument();
    act(() => useAppRunStore.getState().setStatus("syk-inn", "running"));
    expect(announce).toHaveBeenCalledWith(copy["live.appRunning"]("Sykmelding"));
    expect(
      screen.getByText(copy["s5.status.running"]("09:14", "10:14")),
    ).toBeInTheDocument();
  });

  it("calls the browser history for back and forward and announces them", async () => {
    const back = vi.spyOn(window.history, "back").mockImplementation(() => {});
    const forward = vi.spyOn(window.history, "forward").mockImplementation(() => {});
    act(() => useAppRunStore.getState().setStatus("syk-inn", "running"));
    const { announce } = setup();
    await userEvent.click(screen.getByRole("button", { name: "Tilbake i Sykmelding" }));
    await userEvent.click(screen.getByRole("button", { name: "Frem i Sykmelding" }));
    expect(back).toHaveBeenCalledOnce();
    expect(forward).toHaveBeenCalledOnce();
    expect(announce).toHaveBeenCalledWith(copy["live.back"]("Sykmelding"));
    expect(announce).toHaveBeenCalledWith(copy["live.forward"]("Sykmelding"));
    back.mockRestore();
    forward.mockRestore();
  });

  it("disables back and forward while the app is starting", () => {
    setup();
    expect(screen.getByRole("button", { name: "Tilbake i Sykmelding" })).toBeDisabled();
  });

  it("toggles the developer panel", async () => {
    setup();
    const panel = document.getElementById("dev-panel") as HTMLElement;
    expect(panel).not.toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: copy["s5.dev"] }));
    expect(panel).toBeVisible();
    expect(screen.getByRole("button", { name: copy["s5.dev"] })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await userEvent.click(screen.getByRole("button", { name: copy["dev.close"] }));
    expect(panel).not.toBeVisible();
  });

  it("closes the app and its tab", async () => {
    setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s5.close"] }));
    expect(useAppRunStore.getState().runs).toEqual([]);
    expect(useWorkspaceStore.getState().tabs.map((t) => t.id)).toEqual(["start"]);
  });

  it("hides the app and flags the context when the journal shows another patient", async () => {
    seedJournal(kari);
    useWorkspaceStore.getState().openTab({ kind: "journal", label: "Journal" });
    setup();
    expect(document.querySelector("iframe")).toBeNull();
    expect(
      screen.getByRole("region", { name: copy["context.label"] }),
    ).toHaveTextContent(copy["context.stale"]);
    expect(
      screen.getAllByText(copy["s5.status.stale"]("Ola Nordmann")).length,
    ).toBeGreaterThan(0);
    await userEvent.click(
      screen.getByRole("button", { name: copy["s5.stale.open"]("Kari Hansen") }),
    );
    expect(useWorkspaceStore.getState().current).toBe("journal");
    expect(navigate).toHaveBeenCalledWith({
      to: "/patients/$patientId",
      params: { patientId: "p2" },
    });
  });

  it("shows the session state on every action", () => {
    act(() => useAppRunStore.getState().setStatus("syk-inn", "session"));
    setup();
    expect(screen.getByRole("alert")).toHaveTextContent(copy["s5.session.title"]);
    expect(
      screen.getByRole("button", { name: copy["s5.reload"]("Sykmelding") }),
    ).toBeDisabled();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = setup();
    await expectNoSeriousViolations(container, { iframes: false });
  });
});
