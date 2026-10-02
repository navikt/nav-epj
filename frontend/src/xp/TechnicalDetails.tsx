import { Fragment, useId, useState } from "react";
import { Button } from "./Button";
import { copy } from "./copy";

type Props = {
  items: ReadonlyArray<readonly [string, string]>;
  onCopy: () => void;
};

export function TechnicalDetails({ items, onCopy }: Props) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <div className="xp-details">
      <button
        type="button"
        className="sum"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
      >
        <span aria-hidden="true">{open ? "▾" : "▸"}</span>
        {copy["s8.details"]}
      </button>
      <div id={panelId} hidden={!open}>
        <dl>
          {items.map(([key, value]) => (
            <Fragment key={key}>
              <dt>{key}</dt>
              <dd>{value}</dd>
            </Fragment>
          ))}
        </dl>
        <div className="xp-pad">
          <Button variant="small" onClick={onCopy}>
            {copy["s8.details.copy"]}
          </Button>
        </div>
      </div>
    </div>
  );
}
