import { Activity, type ReactNode } from "react";
import { AppTabView } from "./AppTabView";
import { DocumentTabs } from "./DocumentTabs";
import { HjelpPage } from "./HjelpPage";
import { KontrollpanelPage } from "./KontrollpanelPage";
import { SysinfoPage } from "./SysinfoPage";
import { APP_TAB_PREFIX } from "./appInfo";
import { WORK_PANEL_ID } from "./shellContext";
import { useWorkspaceStore, type Tab, type TabKind } from "./workspaceStore";

const SYSTEM_PAGES: readonly (TabKind | undefined)[] = [
  "kontrollpanel",
  "sysinfo",
  "hjelp",
];

function isSystemPage(kind: TabKind | undefined) {
  return SYSTEM_PAGES.includes(kind);
}

type Props = {
  onActivateTab?: (tab: Tab) => void;
  onBeforeCloseTab?: (tab: Tab) => boolean;
  children: ReactNode;
};

export function Workspace({
  onActivateTab,
  onBeforeCloseTab,
  children,
}: Props) {
  const current = useWorkspaceStore((s) => s.current);
  const tabs = useWorkspaceStore((s) => s.tabs);
  const appCurrent = current.startsWith(APP_TAB_PREFIX);
  const systemCurrent = tabs.find((t) => t.id === current)?.kind;
  const keepHidden = appCurrent || isSystemPage(systemCurrent);
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
        <div className="xp-keep" hidden={keepHidden}>
          <Activity mode={keepHidden ? "hidden" : "visible"}>{children}</Activity>
        </div>
        {systemCurrent === "kontrollpanel" && <KontrollpanelPage />}
        {systemCurrent === "sysinfo" && <SysinfoPage />}
        {systemCurrent === "hjelp" && <HjelpPage />}
        {tabs.map((tab) =>
          tab.kind === "app" ? (
            <div
              key={tab.id}
              className="xp-appview"
              hidden={tab.id !== current}
            >
              <AppTabView clientId={tab.clientId} />
            </div>
          ) : null,
        )}
      </div>
    </main>
  );
}
