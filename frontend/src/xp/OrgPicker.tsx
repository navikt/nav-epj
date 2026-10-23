import { useState } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { Card } from "./Card";
import { XpIcon } from "./XpIcon";
import { copy } from "./copy";
import type { SessionOrg } from "../utils/mapping/epj";

type Props = {
  sessionOrg: SessionOrg;
  onChoose: (orgnummer: string) => Promise<void>;
  onCancel?: () => void;
};

export function OrgPicker({ sessionOrg, onChoose, onCancel }: Props) {
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function choose(orgnummer: string) {
    setPending(true);
    setFailed(false);
    try {
      await onChoose(orgnummer);
    } catch {
      setFailed(true);
      setPending(false);
    }
  }

  return (
    <Card heading={copy["org.pick.title"]} headingId="org-pick-title">
      <p>{copy["org.pick.body"]}</p>
      {failed && (
        <div className="xp-note error" role="alert">
          <XpIcon name="feil" />
          <div>{copy["org.pick.error"]}</div>
        </div>
      )}
      {sessionOrg.choosableOrgs.length === 0 ? (
        <p>{copy["org.pick.empty"]}</p>
      ) : (
        <ul aria-labelledby="org-pick-title">
          {sessionOrg.choosableOrgs.map(({ org, hasOwnList }) => (
            <li key={org.id}>
              <Button
                disabled={pending}
                onClick={() => org.orgnummer && void choose(org.orgnummer)}
              >
                {org.navn}
                {org.orgnummer && ` · ${copy["org.pick.orgnr"](org.orgnummer)}`}
              </Button>{" "}
              {hasOwnList && <Badge tone="info">{copy["org.pick.ownList"]}</Badge>}
            </li>
          ))}
        </ul>
      )}
      {onCancel && (
        <Button variant="link" onClick={onCancel} disabled={pending}>
          {copy["org.pick.cancel"]}
        </Button>
      )}
    </Card>
  );
}
