import type { ReactNode } from "react";
import { Button } from "./Button";
import { Card } from "./Card";
import { Note } from "./Note";
import { copy } from "./copy";
import type { Loadable } from "./useSystemInfo";

type Props<T> = {
  heading: string;
  headingId: string;
  state: Loadable<T>;
  onRetry: () => void;
  children: (data: T) => ReactNode;
};

export function SysinfoSection<T>({
  heading,
  headingId,
  state,
  onRetry,
  children,
}: Props<T>) {
  return (
    <Card heading={heading} headingId={headingId}>
      {state.status === "loading" && (
        <p role="status">{copy["common.loading"]}</p>
      )}
      {state.status === "error" && (
        <>
          <Note tone="error" role="alert">
            {copy["s8.NETWORK.head"]}
          </Note>
          <div className="xp-form-actions">
            <Button onClick={onRetry}>{copy["s8.NETWORK.action"]}</Button>
          </div>
        </>
      )}
      {state.status === "ready" && children(state.data)}
    </Card>
  );
}
