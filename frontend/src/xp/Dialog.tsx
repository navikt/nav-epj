import {
  useEffect,
  useId,
  useRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { copy } from "./copy";
import { useModalStore } from "./modalStore";
import { useShell } from "./shellContext";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

type Props = {
  title: string;
  role?: "dialog" | "alertdialog";
  labelledBy?: string;
  describedBy?: string;
  wide?: boolean;
  onClose: () => void;
  children: ReactNode;
};

export function Dialog({
  title,
  role = "dialog",
  labelledBy,
  describedBy,
  wide,
  onClose,
  children,
}: Props) {
  const { rootElement } = useShell();
  const titleId = useId();
  const windowRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = windowRef.current;
    if (!node) return;
    const dialog: HTMLDivElement = node;
    const opener = document.activeElement as HTMLElement | null;
    const background =
      rootElement?.querySelector<HTMLElement>(":scope > .xp-app") ?? null;
    background?.setAttribute("inert", "");

    const focusables = () =>
      Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
    const initial =
      dialog.querySelector<HTMLElement>("[data-autofocus]") ??
      dialog.querySelector<HTMLElement>(".xp-btn.is-default") ??
      dialog
        .querySelector<HTMLElement>(".xp-body")
        ?.querySelector<HTMLElement>(FOCUSABLE) ??
      dialog;
    initial.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Tab") {
        const items = focusables();
        if (items.length === 0) {
          event.preventDefault();
          return;
        }
        const first = items[0];
        const last = items[items.length - 1];
        const active = document.activeElement;
        if (event.shiftKey && (active === first || active === dialog)) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && active === last) {
          event.preventDefault();
          first.focus();
        } else if (!dialog.contains(active)) {
          event.preventDefault();
          first.focus();
        }
      }
    }

    useModalStore.getState().enter();
    document.addEventListener("keydown", onKeyDown, true);
    return () => {
      useModalStore.getState().leave();
      document.removeEventListener("keydown", onKeyDown, true);
      background?.removeAttribute("inert");
      if (opener?.isConnected) opener.focus();
    };
  }, [rootElement]);

  function onEscape(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Escape" || event.defaultPrevented) return;
    event.preventDefault();
    event.stopPropagation();
    onClose();
  }

  return createPortal(
    <div className="xp-modal-layer">
      <div
        ref={windowRef}
        className={wide ? "xp-window xp-dialog wide" : "xp-window xp-dialog"}
        role={role}
        aria-modal="true"
        aria-labelledby={labelledBy ?? titleId}
        aria-describedby={describedBy}
        tabIndex={-1}
        onKeyDown={onEscape}
      >
        <div className="xp-titlebar">
          <span className="xp-title" id={titleId}>
            {title}
          </span>
          <div className="xp-tbtns">
            <button
              type="button"
              className="xp-tbtn close"
              aria-label={copy["common.closeDialog"]}
              title={copy["common.close"]}
              onClick={onClose}
            >
              <svg
                width="14"
                height="14"
                viewBox="0 0 14 14"
                aria-hidden="true"
                focusable="false"
              >
                <path
                  d="M2 2l10 10M12 2L2 12"
                  stroke="currentColor"
                  strokeWidth="2"
                  fill="none"
                />
              </svg>
            </button>
          </div>
        </div>
        <div className="xp-body">{children}</div>
      </div>
    </div>,
    rootElement ?? document.body,
  );
}
