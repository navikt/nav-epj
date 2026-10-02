import { useState } from "react";
import { AppPropertiesDialog } from "./AppPropertiesDialog";
import { Button } from "./Button";
import { Note } from "./Note";
import { XpIcon } from "./XpIcon";
import { APP_MODE_LABELS, appIconName } from "./appInfo";
import { useAppsStore } from "./appsStore";
import { copy } from "./copy";
import type { App } from "../utils/mapping/epj";

export function KontrollAppsPage() {
  const status = useAppsStore((s) => s.status);
  const apps = useAppsStore((s) => s.apps);
  const [selected, setSelected] = useState<App | null>(null);

  if (status === "error") {
    return (
      <>
        <Note tone="error" role="alert">
          {copy["s8.NETWORK.head"]}
        </Note>
        <div className="xp-form-actions">
          <Button onClick={() => void useAppsStore.getState().load()}>
            {copy["s8.NETWORK.action"]}
          </Button>
        </div>
      </>
    );
  }

  if (status !== "ready") {
    return (
      <p role="status" className="xp-notice">
        {copy["common.loading"]}
      </p>
    );
  }

  return (
    <>
      <div className="xp-card xp-tablewrap">
        <table className="xp-list" aria-label={copy["s9.cat.apps"]}>
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s9.apps.col.app"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s9.apps.col.id"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s9.apps.col.mode"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s9.apps.col.auth"]}</span>
              </th>
              <th scope="col">
                <span className="h sr-only">{copy["s9.apps.props"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {apps.map((app) => (
              <tr key={app.clientId}>
                <td>
                  <span className="xp-namecell">
                    <XpIcon name={appIconName(app.ikon)} /> {app.navn}
                  </span>
                </td>
                <td className="mono">{app.clientId}</td>
                <td>{APP_MODE_LABELS[app.launchMode]}</td>
                <td className="mono">
                  {app.tokenEndpointAuthMethod ?? copy["s4.empty.value"]}
                </td>
                <td>
                  <Button
                    variant="small"
                    aria-label={copy["s10.title"](app.navn)}
                    onClick={() => setSelected(app)}
                  >
                    {copy["s9.apps.props"]}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="xp-hint">{copy["s9.apps.note"]}</p>
      {selected && (
        <AppPropertiesDialog app={selected} onClose={() => setSelected(null)} />
      )}
    </>
  );
}
