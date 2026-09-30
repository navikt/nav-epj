import { useEffect, useRef } from "react";
import { copy } from "./copy";
import { START_TAB_ID, useWorkspaceStore } from "./workspaceStore";

export type TabRoute = "/" | "/patients";

function normalize(pathname: string) {
  return pathname.replace(/\/+$/, "") || "/";
}

export function useRouteTabSync(
  pathname: string,
  navigate: (to: TabRoute) => void,
) {
  const pathnameRef = useRef(pathname);
  const navigateRef = useRef(navigate);

  useEffect(() => {
    pathnameRef.current = pathname;
    navigateRef.current = navigate;
  });

  useEffect(() => {
    const { openTab, setCurrent } = useWorkspaceStore.getState();
    const path = normalize(pathname);
    if (path === "/") {
      setCurrent(START_TAB_ID);
    } else if (path === "/patients") {
      openTab({ kind: "patients", label: copy["pane.system.patients"] });
    }
  }, [pathname]);

  useEffect(
    () =>
      useWorkspaceStore.subscribe((state, previous) => {
        if (state.current === previous.current) return;
        const path = normalize(pathnameRef.current);
        if (state.current === START_TAB_ID && path !== "/") {
          navigateRef.current("/");
        } else if (state.current === "patients" && path !== "/patients") {
          navigateRef.current("/patients");
        }
      }),
    [],
  );
}
