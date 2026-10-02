import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ChooseViewDialog } from "./ChooseViewDialog";
import { seedApps, seedJournal, validator } from "./appFixtures";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { useLaunchModeStore } from "./launchModeStore";
import { useAppRunStore } from "./appRunStore";

function setup() {
  const onClose = vi.fn();
  const view = render(<ChooseViewDialog app={validator} onClose={onClose} />);
  return { ...view, onClose };
}

describe("ChooseViewDialog", () => {
  beforeEach(() => {
    localStorage.clear();
    seedApps();
    seedJournal();
    useLaunchModeStore.getState().setOwner("9144889");
    vi.stubGlobal("open", vi.fn().mockReturnValue({}));
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        status: 200,
        json: async () => ({ launchUrl: "https://v.example/?launch=1" }),
      })),
    );
  });

  it("is a labelled dialog asking about app and patient", () => {
    setup();
    const dialog = screen.getByRole("dialog", {
      name: copy["s6b.question"]("Validator", "Ola Nordmann"),
    });
    expect(dialog).toHaveTextContent(copy["s6b.title"]("Validator"));
    expect(dialog).toHaveAccessibleDescription(copy["s6b.rememberHint"]);
    expect(screen.getByText(copy["s6b.question"]("Validator", "Ola Nordmann"))).toBeInTheDocument();
  });

  it("offers the two views with the window view preselected", () => {
    setup();
    expect(screen.getByRole("radio", { name: copy["s6b.iframe"] })).toBeChecked();
    expect(screen.getByRole("radio", { name: copy["s6b.tab"] })).not.toBeChecked();
    expect(screen.getByRole("group", { name: copy["s6b.legend"] })).toBeInTheDocument();
  });

  it("starts in a window without remembering by default", async () => {
    const { onClose } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["s6b.start"] }));
    expect(onClose).toHaveBeenCalledOnce();
    await vi.waitFor(() => expect(useAppRunStore.getState().runs).toHaveLength(1));
    expect(useLaunchModeStore.getState().choices).toEqual({});
  });

  it("starts in a browser tab when chosen and remembers it per browser user", async () => {
    setup();
    await userEvent.click(screen.getByRole("radio", { name: copy["s6b.tab"] }));
    await userEvent.click(screen.getByRole("checkbox", { name: copy["s6b.remember"] }));
    await userEvent.click(screen.getByRole("button", { name: copy["s6b.start"] }));
    await vi.waitFor(() => expect(window.open).toHaveBeenCalledOnce());
    expect(useLaunchModeStore.getState().choices).toEqual({ validator: "tab" });
    expect(localStorage.getItem("nav-epj:launchMode:9144889")).toBe('{"validator":"tab"}');
  });

  it("submits with Enter", async () => {
    const { onClose } = setup();
    await userEvent.keyboard("{Enter}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("cancels without starting anything", async () => {
    const { onClose } = setup();
    await userEvent.click(screen.getByRole("button", { name: copy["common.cancel"] }));
    expect(onClose).toHaveBeenCalledOnce();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("has no serious accessibility violations", async () => {
    setup();
    await expectNoSeriousViolations(document.body);
  });
});
