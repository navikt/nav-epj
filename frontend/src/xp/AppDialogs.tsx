import { ChooseViewDialog } from "./ChooseViewDialog";
import { LaunchErrorDialog } from "./LaunchErrorDialog";
import { TabAppDialog } from "./TabAppDialog";
import { useAppDialogStore } from "./appDialogStore";

export function AppDialogs() {
  const dialog = useAppDialogStore((s) => s.dialog);
  const close = useAppDialogStore((s) => s.close);
  if (!dialog) return null;
  switch (dialog.kind) {
    case "error":
      return <LaunchErrorDialog dialog={dialog} onClose={close} />;
    case "ask":
      return <ChooseViewDialog app={dialog.app} onClose={close} />;
    case "tabApp":
      return <TabAppDialog clientId={dialog.clientId} onClose={close} />;
  }
}
