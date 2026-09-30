import { useState } from "react";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";
import {
  ageOn,
  birthDateOf,
  formatDate,
  fullName,
  genderLabel,
  genderOf,
  maskPersonident,
} from "./patientInfo";
import { useShell } from "./shellContext";
import { useNow } from "./useNow";
import { XpIcon } from "./XpIcon";
import type { Pasient } from "../utils/mapping/epj";

type Props = {
  pasient: Pasient;
  compact?: boolean;
  stale?: boolean;
};

export function PatientContext({ pasient, compact, stale }: Props) {
  const user = useCurrentUser();
  const { announce } = useShell();
  const now = useNow(60_000);
  const [revealed, setRevealed] = useState(false);
  const birthDate = birthDateOf(pasient);
  const showOffice = !compact && user !== null;
  const name = fullName(pasient);

  function toggle() {
    const next = !revealed;
    setRevealed(next);
    announce(next ? copy["live.fnrShown"] : copy["live.fnrHidden"]);
  }

  return (
    <section
      role="region"
      aria-label={copy["context.label"]}
      className={compact ? "xp-banner compact" : "xp-banner"}
    >
      {compact ? (
        <p className="name">{name}</p>
      ) : (
        <h1 className="name">
          <XpIcon name="pasient" size={32} />
          {name}
        </h1>
      )}
      <dl className="kvs">
        <div className="kv">
          <dt>{copy["context.fnr"]}</dt>
          <dd>
            <span className="mono">
              {revealed ? pasient.personident : maskPersonident(pasient.personident)}
            </span>
            <button
              type="button"
              className="xp-btn link"
              aria-pressed={revealed}
              onClick={toggle}
            >
              {revealed ? copy["context.fnrHide"] : copy["context.fnrShow"]}
            </button>
          </dd>
        </div>
        <div className="kv">
          <dt>{copy["context.born"]}</dt>
          <dd>{birthDate ? formatDate(birthDate) : "–"}</dd>
        </div>
        <div className="kv">
          <dt>{copy["context.age"]}</dt>
          <dd>{birthDate ? copy["context.ageValue"](ageOn(birthDate, now)) : "–"}</dd>
        </div>
        <div className="kv">
          <dt>{copy["context.gender"]}</dt>
          <dd>{genderLabel(genderOf(pasient))}</dd>
        </div>
        {showOffice && (
          <>
            <div className="kv">
              <dt>{copy["context.office"]}</dt>
              <dd>{user.legekontor}</dd>
            </div>
            {user.orgnummer && (
              <div className="kv">
                <dt>{copy["context.orgnr"]}</dt>
                <dd>{user.orgnummer}</dd>
              </div>
            )}
          </>
        )}
        {stale && (
          <div className="kv">
            <dd>{copy["context.stale"]}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}
