import { useCallback } from "react";
import { copy } from "./copy";
import type { LaunchChoice } from "./launchModeStore";
import { findApp, popOutApp, startApp } from "./launchApp";
import { useShell } from "./shellContext";
import type { App } from "../utils/mapping/epj";

export function useStartApp() {
  const { announce } = useShell();
  const start = useCallback(
    async (app: App, choice?: LaunchChoice) => {
      if ((await startApp(app, choice)) === "tab") {
        announce(copy["live.appTab"](app.navn));
      }
    },
    [announce],
  );
  const popOut = useCallback(
    async (clientId: string) => {
      const app = findApp(clientId);
      if ((await popOutApp(clientId)) === "tab" && app) {
        announce(copy["live.appTab"](app.navn));
      }
    },
    [announce],
  );
  return { start, popOut };
}
