import "@testing-library/jest-dom/vitest";
import { afterEach, expect } from "vitest";
import { cleanup } from "@testing-library/react";
import * as axeMatchers from "vitest-axe/matchers";
import { useModalStore } from "./modalStore";
import { readPreferences, usePreferencesStore } from "./preferencesStore";
import { useWorkspaceStore } from "./workspaceStore";

expect.extend(axeMatchers);

afterEach(() => {
  cleanup();
  localStorage.clear();
  useWorkspaceStore.getState().reset();
  usePreferencesStore.setState(readPreferences());
  useModalStore.setState({ openCount: 0 });
});

HTMLCanvasElement.prototype.getContext = () => null;
