import { Fragment } from "react";
import type { Row } from "./sysinfoModel";

export function SysinfoRows({ rows }: { rows: Row[] }) {
  return (
    <dl className="xp-dl">
      {rows.map(([label, value]) => (
        <Fragment key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </Fragment>
      ))}
    </dl>
  );
}
