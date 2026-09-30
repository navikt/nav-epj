import { MessageBox } from "./MessageBox";
import { copy } from "./copy";

type Props = {
  onSaveAndClose: () => void;
  onCloseWithoutSaving: () => void;
  onCancel: () => void;
};

export function UnsavedCloseDialog({
  onSaveAndClose,
  onCloseWithoutSaving,
  onCancel,
}: Props) {
  return (
    <MessageBox
      title={copy["s4.unsavedDlg.title"]}
      heading={copy["s4.unsavedDlg.head"]}
      variant="advarsel"
      onClose={onCancel}
      buttons={[
        { label: copy["s4.unsavedDlg.saveClose"], onClick: onSaveAndClose, isDefault: true },
        { label: copy["s4.unsavedDlg.close"], onClick: onCloseWithoutSaving },
        { label: copy["common.cancel"], onClick: onCancel },
      ]}
    >
      <p>{copy["s4.unsavedDlg.body"]}</p>
    </MessageBox>
  );
}
