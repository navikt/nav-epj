import { Card } from "./Card";
import { Note } from "./Note";
import { XpIcon } from "./XpIcon";
import { copy } from "./copy";

const SHORTCUTS = [
  copy["s13.keys.search"],
  copy["s13.keys.close"],
  copy["s13.keys.tabs"],
  copy["s13.keys.f6"],
  copy["s13.keys.arrows"],
  copy["s13.keys.esc"],
].map((text) => {
  const [key, ...what] = text.split(" – ");
  return { key, what: what.join(" – ") };
});

export function HjelpPage() {
  return (
    <>
      <div className="xp-pagehead">
        <XpIcon name="hjelp" size={32} />
        <h1 className="xp-h1">{copy["s13.title"]}</h1>
      </div>
      <Card heading={copy["s13.keys.title"]} headingId="help-keys">
        <table className="xp-list" aria-labelledby="help-keys">
          <thead>
            <tr>
              <th scope="col">
                <span className="h">{copy["s13.keys.col.key"]}</span>
              </th>
              <th scope="col">
                <span className="h">{copy["s13.keys.col.what"]}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {SHORTCUTS.map(({ key, what }) => (
              <tr key={key}>
                <td>
                  <kbd className="mono">{key}</kbd>
                </td>
                <td>{what}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="xp-hint">{copy["s13.keys.note"]}</p>
      </Card>
      <Card heading={copy["s13.mode.title"]} headingId="help-mode">
        <p>{copy["s13.mode.iframe"]}</p>
        <p>{copy["s13.mode.tab"]}</p>
      </Card>
      <Card heading={copy["s13.switch.title"]} headingId="help-switch">
        <p>{copy["s13.switch.body"]}</p>
        <Note tone="warn">{copy["s13.switch.warning"]}</Note>
      </Card>
      <Card heading={copy["s13.test.title"]} headingId="help-test">
        <p>{copy["s13.test.body"]}</p>
      </Card>
    </>
  );
}
