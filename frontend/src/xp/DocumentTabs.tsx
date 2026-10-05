import { useEffect, useRef, type KeyboardEvent } from "react";
import { copy } from "./copy";
import { XpIcon, type IconName } from "./XpIcon";
import { WORK_PANEL_ID } from "./shellContext";
import { useWorkspaceStore, type Tab, type TabKind } from "./workspaceStore";

const icons: Record<TabKind, IconName> = {
  start: "hjem",
  patients: "pasienter",
  journal: "journal",
  app: "vindu",
  kontrollpanel: "kontrollpanel",
  sysinfo: "systeminfo",
  hjelp: "hjelp",
  hendelseslogg: "hendelseslogg",
};

type Props = {
  onActivate?: (tab: Tab) => void;
  onBeforeClose?: (tab: Tab) => boolean;
};

export function DocumentTabs({ onActivate, onBeforeClose }: Props) {
  const tabs = useWorkspaceStore((s) => s.tabs);
  const current = useWorkspaceStore((s) => s.current);
  const setCurrent = useWorkspaceStore((s) => s.setCurrent);
  const closeTab = useWorkspaceStore((s) => s.closeTab);
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const pendingFocus = useRef<string | null>(null);

  useEffect(() => {
    if (pendingFocus.current) {
      buttons.current.get(pendingFocus.current)?.focus();
      pendingFocus.current = null;
    }
  });

  function activate(tab: Tab, moveFocus: boolean) {
    setCurrent(tab.id);
    onActivate?.(tab);
    if (moveFocus) {
      const button = buttons.current.get(tab.id);
      button?.focus();
      button?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
    }
  }

  function close(tab: Tab, moveFocus: boolean) {
    if (!tab.closable) return;
    if (onBeforeClose && !onBeforeClose(tab)) return;
    closeTab(tab.id);
    const state = useWorkspaceStore.getState();
    if (moveFocus) pendingFocus.current = state.current;
    if (state.current !== current) {
      const next = state.tabs.find((t) => t.id === state.current);
      if (next) onActivate?.(next);
    }
  }

  function onTabKeyDown(event: KeyboardEvent, index: number) {
    let target: number | null = null;
    if (event.key === "ArrowRight") target = (index + 1) % tabs.length;
    else if (event.key === "ArrowLeft")
      target = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") target = 0;
    else if (event.key === "End") target = tabs.length - 1;
    else if (event.key === "Delete") {
      event.preventDefault();
      close(tabs[index], true);
      return;
    }
    if (target !== null) {
      event.preventDefault();
      activate(tabs[target], true);
    }
  }

  return (
    <div
      className="xp-doctabs"
      role="tablist"
      aria-label={copy["tabs.label"]}
    >
      {tabs.map((tab, index) => {
        const selected = tab.id === current;
        const className = [
          "xp-doctab",
          selected && "is-current",
          tab.error && "is-error",
        ]
          .filter(Boolean)
          .join(" ");
        return (
          <div key={tab.id} className={className} role="presentation">
            <button
              type="button"
              role="tab"
              id={`tab-${tab.id}`}
              className="sel"
              aria-selected={selected}
              aria-controls={WORK_PANEL_ID}
              aria-label={tab.ariaLabel}
              tabIndex={selected ? 0 : -1}
              ref={(el) => {
                if (el) buttons.current.set(tab.id, el);
                else buttons.current.delete(tab.id);
              }}
              onClick={() => activate(tab, false)}
              onKeyDown={(event) => onTabKeyDown(event, index)}
            >
              <XpIcon name={icons[tab.kind]} />
              <span className="lbl">
                {tab.label}
                {tab.unsaved && " •"}
              </span>
              {tab.mark && <span className="mk">{tab.mark}</span>}
            </button>
            {tab.closable && (
              <button
                type="button"
                className="x"
                aria-label={copy["tabs.close"](tab.label)}
                aria-hidden="true"
                title={copy["tabs.closeTooltip"]}
                tabIndex={-1}
                onClick={() => close(tab, true)}
              >
                <svg
                  width="10"
                  height="10"
                  viewBox="0 0 10 10"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path
                    d="M1 1l8 8M9 1l-8 8"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    fill="none"
                  />
                </svg>
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
