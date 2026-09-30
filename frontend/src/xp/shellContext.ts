import { createContext, useContext } from "react";

export const MENU_BUTTON_ID = "xp-menu-button";
export const TASK_PANE_ID = "xp-task-pane";
export const SEARCH_INPUT_ID = "xp-search";
export const WORK_PANEL_ID = "work-panel";

export type ShellContextValue = {
  narrow: boolean;
  drawerOpen: boolean;
  setDrawerOpen: (open: boolean) => void;
  announce: (message: string) => void;
  rootElement: HTMLElement | null;
};

export const ShellContext = createContext<ShellContextValue>({
  narrow: false,
  drawerOpen: false,
  setDrawerOpen: () => {},
  announce: () => {},
  rootElement: null,
});

export function useShell() {
  return useContext(ShellContext);
}
