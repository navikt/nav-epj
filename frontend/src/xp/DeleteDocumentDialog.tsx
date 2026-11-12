import { useState } from "react";
import { MessageBox } from "./MessageBox";
import { Note } from "./Note";
import { copy } from "./copy";

type Props = {
  title: string;
  onConfirm: () => Promise<boolean>;
  onCancel: () => void;
};

export function DeleteDocumentDialog({ title, onConfirm, onCancel }: Props) {
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function confirm() {
    if (pending) return;
    setPending(true);
    setFailed(false);
    if (!(await onConfirm())) {
      setPending(false);
      setFailed(true);
    }
  }

  function cancel() {
    if (!pending) onCancel();
  }

  return (
    <MessageBox
      title={copy["s4.documents.deleteDlg.title"]}
      heading={copy["s4.documents.deleteDlg.head"](title)}
      variant="warning"
      onClose={cancel}
      buttons={[
        {
          label: copy["common.cancel"],
          onClick: cancel,
          isDefault: true,
          disabled: pending,
        },
        {
          label: pending
            ? copy["s4.documents.deleteDlg.pending"]
            : copy["s4.documents.delete"],
          onClick: () => void confirm(),
          disabled: pending,
        },
      ]}
    >
      <p>{copy["s4.documents.deleteDlg.body"]}</p>
      {failed && (
        <Note tone="error" role="alert">
          {copy["s4.documents.deleteDlg.error"]}
        </Note>
      )}
    </MessageBox>
  );
}
