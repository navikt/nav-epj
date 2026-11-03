import { MessageBox } from "./MessageBox";
import { copy } from "./copy";
import { fullName } from "./patientInfo";
import type { Patient } from "../utils/mapping/ehr";

type Props = {
  patient: Patient;
  unsaved: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function CancelDialog({ patient, unsaved, onConfirm, onCancel }: Props) {
  return (
    <MessageBox
      title={copy["s4.cancelDlg.title"]}
      heading={copy["s4.cancelDlg.head"](fullName(patient))}
      variant="warning"
      onClose={onCancel}
      buttons={[
        { label: copy["common.cancel"], onClick: onCancel, isDefault: true },
        { label: copy["s4.cancelConsultation"], onClick: onConfirm },
      ]}
    >
      <p>{copy["s4.cancelDlg.body"]}</p>
      {unsaved && <p>{copy["s4.cancelDlg.unsaved"]}</p>}
    </MessageBox>
  );
}
