import type { ReactNode } from "react";
import { DocumentTabs } from "./DocumentTabs";
import { WORK_PANEL_ID } from "./shellContext";
import { useWorkspaceStore, type Tab } from "./workspaceStore";

type Props = {
  onActivateTab?: (tab: Tab) => void;
  onBeforeCloseTab?: (tab: Tab) => boolean;
  children: ReactNode;
};

export function Workspace({ onActivateTab, onBeforeCloseTab, children }: Props) {
  const current = useWorkspaceStore((s) => s.current);
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
        className="xp-page"
      >
        {children}
      </div>
    </main>
  );
}
