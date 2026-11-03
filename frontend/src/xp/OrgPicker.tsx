import { useState } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { Card } from "./Card";
import { XpIcon } from "./XpIcon";
import { copy } from "./copy";
import type { SessionOrg } from "../utils/mapping/ehr";

type Props = {
  sessionOrg: SessionOrg;
  onChoose: (orgNumber: string) => Promise<void>;
  onCancel?: () => void;
};

export function OrgPicker({ sessionOrg, onChoose, onCancel }: Props) {
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function choose(orgNumber: string) {
    setPending(true);
    setFailed(false);
    try {
      await onChoose(orgNumber);
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
          <XpIcon name="error" />
          <div>{copy["org.pick.error"]}</div>
        </div>
      )}
      {sessionOrg.choosableOrgs.length === 0 ? (
        <p>{copy["org.pick.empty"]}</p>
      ) : (
        <ul className="xp-orgpick" aria-labelledby="org-pick-title">
          {sessionOrg.choosableOrgs.map(({ org, hasOwnList }) => (
            <li key={org.id}>
              <Button
                disabled={pending}
                onClick={() => org.orgNumber && void choose(org.orgNumber)}
              >
                {org.name}
                {org.orgNumber && ` · ${copy["org.pick.orgNumber"](org.orgNumber)}`}
              </Button>
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
