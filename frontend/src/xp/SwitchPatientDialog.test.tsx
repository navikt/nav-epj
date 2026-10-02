import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SwitchPatientDialog } from "./SwitchPatientDialog";
import { kari, nyFane, ola, seedApps, seedJournal, seedRun } from "./appFixtures";
import { useAppRunStore } from "./appRunStore";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { usePatientsStore } from "./patientsStore";

function setup(props: Partial<Parameters<typeof SwitchPatientDialog>[0]> = {}) {
  const handlers = { onConfirm: vi.fn(), onCancel: vi.fn() };
  const view = render(
    <SwitchPatientDialog
      from="Ola Nordmann"
      toId="p2"
      unsaved={false}
      deepLink={false}
      {...handlers}
      {...props}
    />,
  );
  return { ...handlers, ...view };
}

describe("SwitchPatientDialog with apps", () => {
  beforeEach(() => {
    seedApps();
    seedJournal();
    usePatientsStore.setState({ patients: [ola, kari] });
  });

  it("describes a switch without apps", () => {
    setup();
    expect(
      screen.getByText(copy["s7.body.noApps"]("Ola Nordmann", "Kari Hansen")),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
  });

  it("lists embedded apps as closing and counts them", () => {
    seedRun();
    setup();
    expect(
      screen.getByText(copy["s7.body.apps"]("Ola Nordmann", 1, "Kari Hansen")),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")[1]).toHaveTextContent(
      `Sykmelding · ${copy["s7.mode.iframe"]} · ${copy["s7.effect.close"]}`,
    );
  });

  it("warns that tab apps cannot be closed by the host", () => {
    useAppRunStore.getState().addTabApp({
      id: "smart-ny-fane-1",
      clientId: nyFane.clientId,
      navn: "Fanen",
      patient: ola,
      startedAt: new Date(),
    });
    setup();
    expect(screen.getAllByRole("listitem")[1]).toHaveTextContent(
      `Fanen · ${copy["s7.mode.tab"]} · ${copy["s7.effect.cannot"]}`,
    );
    expect(
      screen.getByText(copy["s7.tabWarning"]("Fanen", "ny-fane", "Ola Nordmann")),
    ).toBeInTheDocument();
  });

  it("ignores tab apps that belong to another patient", () => {
    useAppRunStore.getState().addTabApp({
      id: "smart-ny-fane-1",
      clientId: nyFane.clientId,
      navn: "Fanen",
      patient: kari,
      startedAt: new Date(),
    });
    setup();
    expect(screen.queryByText(/Fanen/)).toBeNull();
  });

  it("makes the switch the Enter default when nothing is unsaved", async () => {
    const { onConfirm } = setup();
    expect(screen.getByRole("button", { name: copy["s7.confirm"] })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("defaults to Avbryt so Enter never discards unsaved changes", async () => {
    const { onConfirm, onCancel } = setup({ unsaved: true });
    expect(screen.getByRole("button", { name: copy["common.cancel"] })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("confirms with the resolved name and cancels", async () => {
    const { onConfirm, onCancel } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s7.confirm"] }));
    expect(onConfirm).toHaveBeenCalledWith("Kari Hansen");
    await userEvent.click(screen.getByRole("button", { name: copy["common.cancel"] }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("has no serious accessibility violations with apps and unsaved changes", async () => {
    seedRun();
    useAppRunStore.getState().addTabApp({
      id: "smart-ny-fane-1",
      clientId: nyFane.clientId,
      navn: "Fanen",
      patient: ola,
      startedAt: new Date(),
    });
    setup({ unsaved: true, deepLink: true });
    await expectNoSeriousViolations(document.body);
  });
});
