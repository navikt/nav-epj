import { Fragment, useId, useState } from "react";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { SubTabs } from "./SubTabs";
import { XpIcon } from "./XpIcon";
import { APP_MODE_LABELS, appIconName } from "./appInfo";
import { copy } from "./copy";
import { explanationOf, groupScopes } from "./scopes";
import type { App } from "../utils/mapping/epj";

type TabId = "gen" | "oauth" | "scope";

type Props = {
  app: App;
  onClose: () => void;
};

const TABS = [
  { id: "gen", label: copy["s10.tab.gen"] },
  { id: "oauth", label: copy["s10.tab.oauth"] },
  { id: "scope", label: copy["s10.tab.scope"] },
] as const;

const EMPTY = copy["s4.empty.value"];

function secretText(method: string | null | undefined) {
  if (method === "client_secret_basic") return copy["s10.secret.basic"];
  if (method === "private_key_jwt") return copy["s10.secret.jwt"];
  return EMPTY;
}

function jwksText(app: App) {
  if (app.jwksUri) return app.jwksUri;
  return app.tokenEndpointAuthMethod === "client_secret_basic"
    ? copy["s10.jwks.none"]
    : EMPTY;
}

function GeneralTab({ app }: { app: App }) {
  return (
    <>
      <div className="xp-appcard-head">
        <XpIcon name={appIconName(app.ikon)} size={48} />
        <div>
          <strong>{app.navn}</strong>
          {app.beskrivelse && <p>{app.beskrivelse}</p>}
        </div>
      </div>
      <dl className="xp-dl">
        <dt>{copy["s10.launchUrl"]}</dt>
        <dd className="mono">{app.launchUri ?? EMPTY}</dd>
        <dt>{copy["s10.mode"]}</dt>
        <dd>{APP_MODE_LABELS[app.launchMode]}</dd>
        <dt>{copy["s9.apps.col.id"]}</dt>
        <dd className="mono">{app.clientId}</dd>
        <dt>{copy["s10.source"]}</dt>
        <dd>{copy["s10.source.config"]}</dd>
      </dl>
    </>
  );
}

function OAuthTab({ app }: { app: App }) {
  return (
    <dl className="xp-dl">
      <dt>{copy["s9.apps.col.id"]}</dt>
      <dd className="mono">{app.clientId}</dd>
      <dt>{copy["s10.auth"]}</dt>
      <dd className="mono">{app.tokenEndpointAuthMethod ?? EMPTY}</dd>
      <dt>{copy["s10.secret"]}</dt>
      <dd>{secretText(app.tokenEndpointAuthMethod)}</dd>
      <dt>{copy["s10.jwks"]}</dt>
      <dd className="mono">{jwksText(app)}</dd>
      <dt>{copy["s10.redirects"]}</dt>
      <dd>
        <ul>
          {app.redirectUris.map((uri) => (
            <li key={uri} className="mono">
              {uri}
            </li>
          ))}
        </ul>
      </dd>
    </dl>
  );
}

function ScopesTab({ app }: { app: App }) {
  return (
    <>
      {groupScopes(app.scopes).map((group) => (
        <Fragment key={group.id}>
          <h3 className="xp-h2">{group.label}</h3>
          <dl className="xp-dl">
            {group.scopes.map((scope) => (
              <Fragment key={scope}>
                <dt className="mono">{scope}</dt>
                <dd>{explanationOf(scope) ?? EMPTY}</dd>
              </Fragment>
            ))}
          </dl>
        </Fragment>
      ))}
    </>
  );
}

export function AppPropertiesDialog({ app, onClose }: Props) {
  const [tab, setTab] = useState<TabId>("gen");
  const idPrefix = useId();
  return (
    <Dialog
      title={copy["s10.title"](app.navn)}
      titleIcon={appIconName(app.ikon)}
      wide
      onClose={onClose}
    >
      <SubTabs<TabId>
        label={copy["s9.apps.props"]}
        idPrefix={idPrefix}
        tabs={[...TABS]}
        selected={tab}
        onSelect={setTab}
      >
        {tab === "gen" && <GeneralTab app={app} />}
        {tab === "oauth" && <OAuthTab app={app} />}
        {tab === "scope" && <ScopesTab app={app} />}
      </SubTabs>
      <div className="xp-msg-actions">
        <Button isDefault onClick={onClose}>
          {copy["common.ok"]}
        </Button>
      </div>
    </Dialog>
  );
}
