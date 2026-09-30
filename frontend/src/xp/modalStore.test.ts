import { describe, expect, it } from "vitest";
import { isModalOpen, useModalStore } from "./modalStore";

describe("modalStore", () => {
  it("counts nested modals", () => {
    const { enter, leave } = useModalStore.getState();
    expect(isModalOpen()).toBe(false);
    enter();
    enter();
    leave();
    expect(isModalOpen()).toBe(true);
    leave();
    expect(isModalOpen()).toBe(false);
  });

  it("never goes below zero", () => {
    useModalStore.getState().leave();
    expect(useModalStore.getState().openCount).toBe(0);
  });
});
