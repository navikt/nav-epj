import { MessageBox } from "./MessageBox";
import { Note } from "./Note";
import { useAppRunStore } from "./appRunStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { usePatientName } from "./usePatientName";

type Props = {
  from: string;
  toId: string;
  unsaved: boolean;
  deepLink: boolean;
  onConfirm: (toName: string) => void;
  onCancel: () => void;
};

export function SwitchPatientDialog({
  from,
  toId,
  unsaved,
  deepLink,
  onConfirm,
  onCancel,
}: Props) {
  const to = usePatientName(toId);
  const fromId = useJournalStore((s) => s.patientId);
  const runs = useAppRunStore((s) => s.runs);
  const allTabApps = useAppRunStore((s) => s.tabApps);
  const tabApps = allTabApps.filter((a) => a.patient.id === fromId);
  const appCount = runs.length + tabApps.length;
  return (
    <MessageBox
      title={copy["s7.title"]}
      heading={copy["s7.head"](from, to)}
      variant="advarsel"
      onClose={onCancel}
      buttons={[
        { label: copy["s7.confirm"], onClick: () => onConfirm(to), isDefault: true },
        { label: copy["common.cancel"], onClick: onCancel },
      ]}
    >
      {deepLink && <p>{copy["s7.deeplink"](to)}</p>}
      <p>
        {appCount > 0
          ? copy["s7.body.apps"](from, appCount, to)
          : copy["s7.body.noApps"](from, to)}
      </p>
      <p>{copy["s7.list"]}</p>
      <ul>
        <li>
          {copy["s7.item.journal"](from)} · {copy["s7.mode.host"]} ·{" "}
          {copy["s7.effect.close"]}
        </li>
        {runs.map((run) => (
          <li key={run.clientId}>
            {run.navn} · {copy["s7.mode.iframe"]} · {copy["s7.effect.close"]}
          </li>
        ))}
        {tabApps.map((app) => (
          <li key={app.clientId}>
            {app.navn} · {copy["s7.mode.tab"]} · {copy["s7.effect.cannot"]}
          </li>
        ))}
      </ul>
      {tabApps.map((app) => (
        <Note key={app.clientId} tone="warn">
          {copy["s7.tabWarning"](app.navn, app.clientId, from)}
        </Note>
      ))}
      {unsaved && <Note tone="error">{copy["s7.unsaved"]}</Note>}
    </MessageBox>
  );
}
