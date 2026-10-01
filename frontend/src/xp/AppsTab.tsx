import { Fragment, useId } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { Note } from "./Note";
import { XpIcon } from "./XpIcon";
import { appIconName } from "./appInfo";
import { useAppRunStore } from "./appRunStore";
import { useAppsStore } from "./appsStore";
import { copy } from "./copy";
import { useAppsEnabled } from "./useAppsEnabled";
import { useStartApp } from "./useStartApp";
import type { App } from "../utils/mapping/epj";

const modeLabels = {
  iframe: copy["pane.apps.mode.iframe"],
  tab: copy["pane.apps.mode.tab"],
  ask: copy["pane.apps.mode.ask"],
} as const;

// s4.apps.help is one copy.md string covering all three launch modes. Split
// it on its own glyph markers (▣/↗/?) so each mode reads as its own line
// instead of one dense run-on sentence, without altering the copy itself.
function ModeHelp() {
  const lines = copy["s4.apps.help"]
    .split(/(?=[▣↗?] )/)
    .map((line) => line.trim())
    .filter(Boolean);
  return (
    <Note tone="info">
      {lines.map((line, index) => (
        <Fragment key={line}>
          {index > 0 && <br />}
          {line}
        </Fragment>
      ))}
    </Note>
  );
}

function AppCard({
  app,
  enabled,
  reasonId,
}: {
  app: App;
  enabled: boolean;
  reasonId: string;
}) {
  const { start } = useStartApp();
  const running = useAppRunStore((s) =>
    s.runs.some((r) => r.clientId === app.clientId),
  );
  const inTab = useAppRunStore((s) =>
    s.tabApps.some((a) => a.clientId === app.clientId),
  );
  const headingId = useId();
  return (
    <section className="xp-card" aria-labelledby={headingId}>
      <div className="xp-appcard-head">
        <XpIcon name={appIconName(app.ikon)} size={32} />
        <h3 id={headingId}>{app.navn}</h3>
        <Badge>{modeLabels[app.launchMode]}</Badge>
      </div>
      {app.beskrivelse && <p>{app.beskrivelse}</p>}
      {running && <Badge tone="ok">{copy["s4.apps.runningHost"]}</Badge>}
      {inTab && <Badge tone="info">{copy["s4.apps.runningTab"]}</Badge>}
      <div className="xp-form-actions">
        <Button
          isDefault
          aria-disabled={!enabled}
          aria-describedby={enabled ? undefined : reasonId}
          onClick={() => {
            if (enabled) void start(app);
          }}
        >
          {copy["s4.apps.start"]}
        </Button>
      </div>
    </section>
  );
}

export function AppsTab() {
  const status = useAppsStore((s) => s.status);
  const apps = useAppsStore((s) => s.apps);
  const enabled = useAppsEnabled();
  const reasonId = useId();

  if (status === "loading" || status === "idle") {
    return (
      <Note tone="info" role="status">
        {copy["common.loading"]}
      </Note>
    );
  }

  if (status === "error") {
    return (
      <div className="xp-note error" role="alert">
        <span>{copy["s8.NETWORK.head"]}</span>
        <Button onClick={() => void useAppsStore.getState().load()}>
          {copy["s8.NETWORK.action"]}
        </Button>
      </div>
    );
  }

  return (
    <>
      {!enabled && (
        <div className="xp-note warn" id={reasonId}>
          <span>
            <b>{copy["s4.apps.disabled.title"]}</b> {copy["s4.apps.disabled.body"]}
          </span>
        </div>
      )}
      <div className="xp-appcards">
        {apps.map((app) => (
          <AppCard
            key={app.clientId}
            app={app}
            enabled={enabled}
            reasonId={reasonId}
          />
        ))}
      </div>
      <ModeHelp />
    </>
  );
}
