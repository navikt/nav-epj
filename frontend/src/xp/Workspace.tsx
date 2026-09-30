import type { ReactNode } from "react";
import { AppTabView } from "./AppTabView";
import { DocumentTabs } from "./DocumentTabs";
import { APP_TAB_PREFIX } from "./appInfo";
import { WORK_PANEL_ID } from "./shellContext";
import { useWorkspaceStore, type Tab } from "./workspaceStore";

type Props = {
  onActivateTab?: (tab: Tab) => void;
  onBeforeCloseTab?: (tab: Tab) => boolean;
  children: ReactNode;
};

export function Workspace({ onActivateTab, onBeforeCloseTab, children }: Props) {
  const current = useWorkspaceStore((s) => s.current);
  const tabs = useWorkspaceStore((s) => s.tabs);
  const appCurrent = current.startsWith(APP_TAB_PREFIX);
  return (
    <main className="xp-work" data-xp-landmark="main" tabIndex={-1}>
      <DocumentTabs
        onActivate={onActivateTab}
        onBeforeClose={onBeforeCloseTab}
      />
      <div
        id={WORK_PANEL_ID}
        role="tabpanel"
        aria-labelledby={`tab-${current}`}
        className={appCurrent ? "xp-page xp-page-app" : "xp-page"}
      >
        <div className="xp-keep" hidden={appCurrent}>
          {children}
        </div>
        {tabs.map((tab) =>
          tab.kind === "app" ? (
            <div key={tab.id} className="xp-appview" hidden={tab.id !== current}>
              <AppTabView clientId={tab.clientId} />
            </div>
          ) : null,
        )}
      </div>
    </main>
  );
}
