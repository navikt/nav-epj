import { useBlocker } from "@tanstack/react-router";
import { useCallback } from "react";
import { SwitchPatientDialog } from "./SwitchPatientDialog";
import { UnsavedCloseDialog } from "./UnsavedCloseDialog";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { focusTab } from "./focusTab";
import {
  needsSwitchConfirmation,
  useJournalGuardStore,
} from "./journalGuardStore";
import { isDirty, useJournalStore } from "./journalStore";
import { fullName } from "./patientInfo";
import { useShell } from "./shellContext";
import { JOURNAL_TAB_ID, useWorkspaceStore } from "./workspaceStore";

const JOURNAL_ROUTE_ID = "/patients/$patientId";

export function JournalGuards() {
  const { announce } = useShell();
  const closeRequested = useJournalGuardStore((s) => s.closeRequested);
  const patient = useJournalStore((s) => s.patient);
  const dirty = useJournalStore((s) => isDirty(s));
  const { cancelClose, setInAppTarget } = useJournalGuardStore.getState();

  const shouldBlockFn = useCallback(
    ({ next }: { next: { routeId: string; params: unknown } }) => {
      if (!next.routeId.startsWith(JOURNAL_ROUTE_ID)) return false;
      const { patientId } = next.params as { patientId?: string };
      return patientId !== undefined && needsSwitchConfirmation(patientId);
    },
    [],
  );
  const enableBeforeUnload = useCallback(
    () => isDirty(useJournalStore.getState()),
    [],
  );
  const blocker = useBlocker({
    shouldBlockFn,
    enableBeforeUnload,
    withResolver: true,
  });
  const inAppTarget = useJournalGuardStore((s) => s.inAppTarget);

  function closeJournal() {
    cancelClose();
    useWorkspaceStore.getState().closeTab(JOURNAL_TAB_ID);
    focusTab(useWorkspaceStore.getState().current);
  }

  async function saveAndClose() {
    cancelClose();
    if (await useJournalStore.getState().save()) {
      useWorkspaceStore.getState().closeTab(JOURNAL_TAB_ID);
      focusTab(useWorkspaceStore.getState().current);
    }
  }

  const target =
    blocker.status === "blocked"
      ? (blocker.next.params as { patientId: string })
      : null;

  function confirmSwitch(toName: string) {
    if (blocker.status !== "blocked" || !patient) return;
    const from = fullName(patient);
    setInAppTarget(null);
    blocker.proceed();
    useBalloonStore.getState().show({
      title: copy["s7.balloon.title"](toName),
      body: copy["s7.balloon.clean"](from),
    });
    announce(copy["live.switched"](from, toName));
    focusTab(JOURNAL_TAB_ID);
  }

  function declineSwitch() {
    if (blocker.status !== "blocked") return;
    setInAppTarget(null);
    blocker.reset();
  }

  return (
    <>
      {closeRequested && (
        <UnsavedCloseDialog
          onSaveAndClose={() => void saveAndClose()}
          onCloseWithoutSaving={closeJournal}
          onCancel={cancelClose}
        />
      )}
      {target && patient && (
        <SwitchPatientDialog
          from={fullName(patient)}
          toId={target.patientId}
          unsaved={dirty}
          deepLink={inAppTarget !== target.patientId}
          onConfirm={confirmSwitch}
          onCancel={declineSwitch}
        />
      )}
    </>
  );
}
