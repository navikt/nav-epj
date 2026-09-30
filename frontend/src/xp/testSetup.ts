import "@testing-library/jest-dom/vitest";
import { afterEach, expect } from "vitest";
import { cleanup } from "@testing-library/react";
import * as axeMatchers from "vitest-axe/matchers";
import { useAppDialogStore } from "./appDialogStore";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { useBalloonStore } from "./balloonStore";
import { useJournalGuardStore } from "./journalGuardStore";
import { useJournalStore } from "./journalStore";
import { resetLaunches } from "./launchApp";
import { useLaunchModeStore } from "./launchModeStore";
import { useModalStore } from "./modalStore";
import { usePatientsStore } from "./patientsStore";
import { readPreferences, usePreferencesStore } from "./preferencesStore";
import { useSessionStore } from "./sessionExpiry";
import { useWorkspaceStore } from "./workspaceStore";

expect.extend(axeMatchers);

afterEach(() => {
  cleanup();
  localStorage.clear();
  useWorkspaceStore.getState().reset();
  usePreferencesStore.setState(readPreferences());
  useModalStore.setState({ openCount: 0 });
  useBalloonStore.setState({ balloon: null });
  usePatientsStore.getState().reset();
  useJournalStore.getState().clear();
  useJournalGuardStore.setState({ closeRequested: false, inAppTarget: null });
  useAppsStore.getState().reset();
  useAppRunStore.getState().reset();
  useAppDialogStore.setState({ dialog: null });
  useLaunchModeStore.getState().reset();
  useSessionStore.setState({ expired: false });
  resetLaunches();
});

HTMLCanvasElement.prototype.getContext = () => null;
