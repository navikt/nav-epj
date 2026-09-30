import { MessageBox } from "./MessageBox";
import { Note } from "./Note";
import { copy } from "./copy";
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
      <p>{copy["s7.body.noApps"](from, to)}</p>
      <p>{copy["s7.list"]}</p>
      <ul>
        <li>
          {copy["s7.item.journal"](from)} · {copy["s7.mode.host"]} ·{" "}
          {copy["s7.effect.close"]}
        </li>
      </ul>
      {unsaved && <Note tone="error">{copy["s7.unsaved"]}</Note>}
    </MessageBox>
  );
}
