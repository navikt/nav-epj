import { useNavigate } from "@tanstack/react-router";
import { format } from "date-fns";
import { useActivePatientStore } from "./activePatientStore";
import { ApiError } from "./api";
import { MessageBox, type MessageBoxVariant } from "./MessageBox";
import { useAppDialogStore, type AppDialog, type AppErrorCode } from "./appDialogStore";
import { useAppsStore } from "./appsStore";
import { useBalloonStore } from "./balloonStore";
import { copyText } from "./clipboard";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { journalRoute } from "./tabRoutes";
import { useStartApp } from "./useStartApp";
import { JOURNAL_TAB_ID, useWorkspaceStore } from "./workspaceStore";

type ErrorDialog = Extract<AppDialog, { kind: "error" }>;

type Props = {
  dialog: ErrorDialog;
  onClose: () => void;
};

const variants: Record<AppErrorCode, MessageBoxVariant> = {
  NO_ACTIVE_PATIENT: "advarsel",
  NO_ACTIVE_ENCOUNTER: "advarsel",
  PATIENT_MISMATCH: "advarsel",
  UNKNOWN_APP: "feil",
  FRAMING_REFUSED: "advarsel",
  SESSION_EXPIRED: "feil",
  NETWORK: "feil",
};

function titleOf(dialog: ErrorDialog) {
  switch (dialog.code) {
    case "FRAMING_REFUSED":
      return copy["s8.FRAMING_REFUSED.title"];
    case "SESSION_EXPIRED":
      return copy["s8.SESSION_EXPIRED.title"];
    case "NETWORK":
      return copy["s8.NETWORK.title"];
    default:
      return copy["s8.title.cannotStart"](dialog.app);
  }
}

function bodyOf(dialog: ErrorDialog) {
  switch (dialog.code) {
    case "NO_ACTIVE_PATIENT":
      return copy["s8.NO_ACTIVE_PATIENT.body"];
    case "NO_ACTIVE_ENCOUNTER":
      return copy["s8.NO_ACTIVE_ENCOUNTER.body"](dialog.app, dialog.patientName);
    case "PATIENT_MISMATCH":
      return copy["s8.PATIENT_MISMATCH.body"](dialog.app, dialog.patientName);
    case "UNKNOWN_APP":
      return copy["s8.UNKNOWN_APP.body"](dialog.app);
    case "FRAMING_REFUSED":
      return copy["s8.FRAMING_REFUSED.body"](dialog.app);
    case "SESSION_EXPIRED":
      return copy["s8.SESSION_EXPIRED.body"];
    case "NETWORK":
      return copy["s8.NETWORK.body"];
  }
}

export function LaunchErrorDialog({ dialog, onClose }: Props) {
  const navigate = useNavigate();
  const { popOut } = useStartApp();
  const { code } = dialog;

  function openJournalTab() {
    const { patientId } = useJournalStore.getState();
    if (!patientId) {
      void navigate({ to: "/patients" });
      return;
    }
    useWorkspaceStore.getState().setCurrent(JOURNAL_TAB_ID);
    void navigate(journalRoute(patientId));
  }

  function claimJournalPatient(source: ErrorDialog) {
    const { patientId } = useJournalStore.getState();
    if (!patientId) {
      void navigate({ to: "/patients" });
      return;
    }
    useActivePatientStore
      .getState()
      .claim(patientId)
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) return;
        useAppDialogStore.getState().show({
          ...source,
          code: "NETWORK",
          status: error instanceof ApiError ? error.status : null,
          call: "PUT /api/active-patient",
          at: new Date(),
          retry: () => claimJournalPatient(source),
        });
      });
  }

  function primary() {
    onClose();
    switch (code) {
      case "NO_ACTIVE_PATIENT":
        void navigate({ to: "/patients" });
        break;
      case "NO_ACTIVE_ENCOUNTER":
        if (useJournalStore.getState().patient) {
          void useJournalStore.getState().start();
          useBalloonStore.getState().show({
            title: copy["s8.title.cannotStart"](dialog.app),
            body: copy["s8.NO_ACTIVE_ENCOUNTER.balloon"](dialog.app),
          });
        }
        openJournalTab();
        break;
      case "PATIENT_MISMATCH":
        claimJournalPatient(dialog);
        break;
      case "UNKNOWN_APP":
        void useAppsStore.getState().load();
        if (useJournalStore.getState().patientId) {
          useJournalStore.getState().setSubTab("apper");
          openJournalTab();
        }
        break;
      case "FRAMING_REFUSED":
        if (dialog.clientId) void popOut(dialog.clientId);
        break;
      case "SESSION_EXPIRED":
        window.location.reload();
        break;
      case "NETWORK":
        dialog.retry?.();
        break;
    }
  }

  const items: [string, string][] = [
    [copy["s8.details.code"], code],
    [copy["s8.details.app"], dialog.clientId ?? dialog.app],
    [copy["s8.details.time"], format(dialog.at, "HH:mm:ss")],
  ];
  if (dialog.status !== null) {
    items.push([copy["s8.details.http"], String(dialog.status)]);
  }
  if (dialog.call) items.push([copy["s8.details.call"], dialog.call]);

  const closeButton = { label: copy["common.close"], onClick: onClose };
  const buttons = [
    {
      label: copy[`s8.${code}.action`],
      onClick: primary,
      isDefault: true,
    },
    ...(code === "SESSION_EXPIRED" ? [] : [closeButton]),
  ];

  return (
    <MessageBox
      title={titleOf(dialog)}
      heading={copy[`s8.${code}.head`]}
      variant={variants[code]}
      wide
      details={{
        items,
        onCopy: () =>
          void copyText(
            items.map(([key, value]) => `${key}: ${value}`).join("\n"),
            copy["s8.details.copied"],
          ),
      }}
      buttons={buttons}
      onClose={onClose}
    >
      <p>{bodyOf(dialog)}</p>
    </MessageBox>
  );
}
