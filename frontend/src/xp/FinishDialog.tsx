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

export function FinishDialog({ patient, unsaved, onConfirm, onCancel }: Props) {
  return (
    <MessageBox
      title={copy["s4.finishDlg.title"]}
      heading={copy["s4.finishDlg.head"](fullName(patient))}
      variant="question"
      onClose={onCancel}
      buttons={[
        { label: copy["s4.finish"], onClick: onConfirm, isDefault: true },
        { label: copy["common.cancel"], onClick: onCancel },
      ]}
    >
      <p>{copy["s4.finishDlg.body"]}</p>
      {unsaved && <p>{copy["s4.finishDlg.unsaved"]}</p>}
    </MessageBox>
  );
}
