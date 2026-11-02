import { useId } from "react";
import { Card } from "./Card";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";

export function KontrollOrgPage() {
  const user = useCurrentUser();
  const officeId = useId();
  const userId = useId();
  if (!user) return <p role="status">{copy["common.loading"]}</p>;
  const empty = copy["s4.empty.value"];
  return (
    <>
      <div className="xp-card-grid">
        <Card heading={copy["s9.org.office"]} headingId={officeId}>
          <dl className="xp-dl">
            <dt>{copy["s9.org.name"]}</dt>
            <dd>{user.clinic}</dd>
            <dt>{copy["s9.org.orgNumber"]}</dt>
            <dd>{user.orgNumber ?? empty}</dd>
            <dt>{copy["s9.org.phone"]}</dt>
            <dd>{user.phone ?? empty}</dd>
          </dl>
        </Card>
        <Card heading={copy["s9.org.user"]} headingId={userId}>
          <dl className="xp-dl">
            <dt>{copy["s9.org.name"]}</dt>
            <dd>{user.name}</dd>
            <dt>{copy["s9.org.hpr"]}</dt>
            <dd>{user.hpr}</dd>
            <dt>{copy["s9.org.auth"]}</dt>
            <dd>{user.qualification}</dd>
            <dt>{copy["s9.org.login"]}</dt>
            <dd>{copy["s9.org.loginValue"]}</dd>
          </dl>
        </Card>
      </div>
      <p className="xp-hint">{copy["s9.org.note"]}</p>
    </>
  );
}
