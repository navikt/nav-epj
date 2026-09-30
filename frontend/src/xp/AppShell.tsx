import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { isModalOpen } from "./modalStore";
import { MENU_BUTTON_ID, ShellContext } from "./shellContext";
import { useNarrow } from "./useNarrow";
import { useTheme } from "./useTheme";
import { useReduceMotion } from "./useReduceMotion";
import { useTextScale } from "./useTextScale";

const BASE_FONT_SIZE_REM = 0.875;

type Props = { children: ReactNode };

export function AppShell({ children }: Props) {
  const narrow = useNarrow();
  const { theme } = useTheme();
  const { effective: reduceMotion } = useReduceMotion();
  const { textScale } = useTextScale();
  const [drawerRequested, setDrawerRequested] = useState(false);
  const [rootElement, setRootElement] = useState<HTMLElement | null>(null);
  const [message, setMessage] = useState("");
  const announceToggle = useRef(false);
  const drawerOpen = narrow && drawerRequested;

  const announce = useCallback((text: string) => {
    announceToggle.current = !announceToggle.current;
    setMessage(announceToggle.current ? text : `${text}\u00a0`);
  }, []);

  const setDrawerOpen = useCallback(
    (open: boolean) => {
      setDrawerRequested(open);
      if (!open) {
        rootElement
          ?.querySelector<HTMLElement>('[data-xp-landmark="main"]')
          ?.focus();
      }
    },
    [rootElement],
  );

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (isModalOpen()) return;
      if (event.key === "F6" && rootElement) {
        event.preventDefault();
        const landmarks = Array.from(
          rootElement.querySelectorAll<HTMLElement>("[data-xp-landmark]"),
        ).filter(
          (el) =>
            !(narrow && !drawerOpen && el.dataset.xpLandmark === "nav"),
        );
        if (landmarks.length === 0) return;
        const active = document.activeElement;
        const index = landmarks.findIndex((el) => el.contains(active));
        const step = event.shiftKey ? -1 : 1;
        const next =
          index === -1
            ? event.shiftKey
              ? landmarks.length - 1
              : 0
            : (index + step + landmarks.length) % landmarks.length;
        landmarks[next].focus();
      } else if (event.key === "Escape" && drawerOpen) {
        setDrawerRequested(false);
        document.getElementById(MENU_BUTTON_ID)?.focus();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [rootElement, narrow, drawerOpen]);

  useEffect(() => {
    if (!narrow) setDrawerRequested(false);
  }, [narrow]);

  useEffect(() => {
    if (drawerOpen) {
      rootElement
        ?.querySelector<HTMLElement>('[data-xp-landmark="nav"]')
        ?.focus();
    }
  }, [drawerOpen, rootElement]);

  const value = useMemo(
    () => ({ narrow, drawerOpen, setDrawerOpen, announce, rootElement }),
    [narrow, drawerOpen, setDrawerOpen, announce, rootElement],
  );

  const style =
    textScale === 100
      ? undefined
      : ({
          "--xp-font-size": `${(BASE_FONT_SIZE_REM * textScale) / 100}rem`,
        } as CSSProperties);

  return (
    <div
      ref={setRootElement}
      className="xp-root xp-viewport"
      data-theme={theme}
      data-reduce={reduceMotion ? "true" : undefined}
      data-narrow={narrow ? "true" : undefined}
      data-drawer={drawerOpen ? "true" : undefined}
      style={style}
    >
      <ShellContext value={value}>
        <div className="xp-app">{children}</div>
        <div className="sr-only" aria-live="polite" aria-atomic="true">
          {message}
        </div>
      </ShellContext>
    </div>
  );
}
