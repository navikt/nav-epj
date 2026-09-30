import { copy } from "./copy";

export function TestMarker() {
  return (
    <span className="xp-test" title={copy["app.testTooltip"]}>
      {copy["app.test"]}
      <span className="sr-only"> {copy["app.testTooltip"]}</span>
    </span>
  );
}
