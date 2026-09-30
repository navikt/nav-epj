import { createContext, useContext } from "react";

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
