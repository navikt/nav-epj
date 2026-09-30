import type { ReactNode } from "react";
import { Button } from "./Button";
import { copy } from "./copy";
import { XpIcon } from "./XpIcon";
import type { HelsepersonellState } from "./useHelsepersonell";

type Props = {
  state: HelsepersonellState;
  onRetry: () => void;
  children: ReactNode;
};

export function UserGate({ state, onRetry, children }: Props) {
  if (state.status === "loading") {
    return (
      <p role="status" className="xp-notice">
        {copy["s1.loading"]}
      </p>
    );
  }
  if (state.status === "error") {
    return (
      <div className="xp-note error" role="alert">
        <XpIcon name="feil" />
        <div>
          <b>{copy["s1.error.title"]}</b>
          <p>{copy["s1.error.body"]}</p>
          <Button onClick={onRetry}>{copy["s1.error.retry"]}</Button>
        </div>
      </div>
    );
  }
  return <>{children}</>;
}
