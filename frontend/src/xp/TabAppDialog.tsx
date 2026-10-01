import { MessageBox } from "./MessageBox";
import { isStaleFor, accessExpiry, useAppRunStore } from "./appRunStore";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { findApp } from "./launchApp";
import { formatTime, fullName } from "./patientInfo";
import { useStartApp } from "./useStartApp";

type Props = {
  tabId: string;
  onClose: () => void;
};

export function TabAppDialog({ tabId, onClose }: Props) {
  const tabApp = useAppRunStore((s) =>
    s.tabApps.find((a) => a.id === tabId),
  );
  const journalPatientId = useJournalStore((s) => s.patientId);
  const journalPatient = useJournalStore((s) => s.patient);
  const { start } = useStartApp();

  if (!tabApp) return null;
  const { clientId } = tabApp;
  const expiry = formatTime(accessExpiry(tabApp.startedAt));
  const owner = fullName(tabApp.patient);
  const stale = isStaleFor(tabApp.patient.id, journalPatientId);
  const remove = () => {
    useAppRunStore.getState().removeTabApp(tabId);
    onClose();
  };

  if (stale) {
    return (
      <MessageBox
        title={copy["s7.staleDlg.title"](tabApp.navn)}
        heading={copy["s7.staleDlg.head"](tabApp.navn, owner)}
        variant="advarsel"
        buttons={[
          { label: copy["s7.staleDlg.done"], onClick: remove, isDefault: true },
          { label: copy["common.close"], onClick: onClose },
        ]}
        onClose={onClose}
      >
        <p>{copy["s7.staleDlg.body1"](expiry)}</p>
        <p>
          {copy["s7.staleDlg.body2"](
            journalPatient ? fullName(journalPatient) : "",
          )}
        </p>
      </MessageBox>
    );
  }

  function restart() {
    const app = findApp(clientId);
    remove();
    if (app) void start(app, "tab");
  }

  return (
    <MessageBox
      title={copy["s7.tabInfo.title"](tabApp.navn)}
      heading={copy["s7.tabInfo.head"](tabApp.navn, owner)}
      variant="info"
      buttons={[
        { label: copy["s7.tabInfo.restart"], onClick: restart, isDefault: true },
        { label: copy["s7.tabInfo.remove"], onClick: remove },
        { label: copy["common.close"], onClick: onClose },
      ]}
      onClose={onClose}
    >
      <p>{copy["s7.tabInfo.body"](expiry)}</p>
    </MessageBox>
  );
}
