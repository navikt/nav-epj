import { MessageBox } from "./MessageBox";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import type { Pasient } from "../utils/mapping/epj";

type Props = {
  patient: Pasient;
  unsaved: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function CancelDialog({ patient, unsaved, onConfirm, onCancel }: Props) {
  return (
    <MessageBox
      title={copy["s4.cancelDlg.title"]}
      heading={copy["s4.cancelDlg.head"](fullName(patient))}
      variant="advarsel"
      onClose={onCancel}
      buttons={[
        { label: copy["common.cancel"], onClick: onCancel, isDefault: true },
        { label: copy["s4.cancelKons"], onClick: onConfirm },
      ]}
    >
      <p>{copy["s4.cancelDlg.body"]}</p>
      {unsaved && <p>{copy["s4.cancelDlg.unsaved"]}</p>}
    </MessageBox>
  );
}
