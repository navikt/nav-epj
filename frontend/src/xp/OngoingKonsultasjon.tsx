import { useId, useMemo, useState } from "react";
import { differenceInMinutes, parseISO } from "date-fns";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { Card } from "./Card";
import { DiagnoseCombobox } from "./DiagnoseCombobox";
import { FinishDialog } from "./FinishDialog";
import { Note } from "./Note";
import { Progress } from "./Progress";
import { TextArea } from "./TextArea";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";
import { diagnoseKey, isDirty, useJournalStore } from "./journalStore";
import { formatDateTime } from "./patientInfo";
import { useShell } from "./shellContext";
import { useNow } from "./useNow";
import type { Konsultasjon, Pasient } from "../utils/mapping/epj";

type Props = {
  patient: Pasient;
  konsultasjon: Konsultasjon;
};

function clockOf(date: Date) {
  return date.toLocaleTimeString("nb-NO", { hour: "2-digit", minute: "2-digit" });
}

export function OngoingKonsultasjon({ patient, konsultasjon }: Props) {
  const titleId = useId();
  const infoId = useId();
  const user = useCurrentUser();
  const { announce } = useShell();
  const now = useNow(30_000);
  const [finishing, setFinishing] = useState(false);
  const draft = useJournalStore((s) => s.draft);
  const baseline = useJournalStore((s) => s.baseline);
  const saveStatus = useJournalStore((s) => s.saveStatus);
  const savedAt = useJournalStore((s) => s.savedAt);
  const dirty = isDirty({ draft, baseline });
  const saving = saveStatus === "saving";
  const minutes = Math.max(
    0,
    differenceInMinutes(now, parseISO(konsultasjon.startetTidspunkt)),
  );
  const savedKeys = useMemo(
    () => new Set(baseline.diagnoser.map(diagnoseKey)),
    [baseline.diagnoser],
  );
  const { addDiagnose, removeDiagnose, setNotat, save } =
    useJournalStore.getState();

  async function confirmFinish() {
    setFinishing(false);
    const ok = await save({ ferdigstill: true });
    if (ok) announce(copy["live.konsDone"]);
  }

  return (
    <div className="xp-cols">
      <div className="xp-grow">
        <Card heading={copy["s4.ongoing.title"]} headingId={titleId}>
          <div>
            <Badge tone="info">{copy["s4.ongoing.badge"](minutes)}</Badge>
          </div>
          <DiagnoseCombobox
            selected={draft.diagnoser}
            savedKeys={savedKeys}
            disabled={saving}
            onAdd={addDiagnose}
            onRemove={removeDiagnose}
          />
          <TextArea
            label={copy["s4.note.label"]}
            rows={6}
            value={draft.notat}
            readOnly={saving}
            onChange={(event) => setNotat(event.target.value)}
          />
          <div className="xp-form-actions">
            <Button
              isDefault
              aria-disabled={saving}
              onClick={() => void save()}
            >
              {saving ? copy["s4.saving"] : copy["s4.save"]}
            </Button>
            <Button
              aria-disabled={saving}
              onClick={() => {
                if (!saving) setFinishing(true);
              }}
            >
              {copy["s4.finish"]}
            </Button>
            <span className="xp-status-text" role="status">
              {saveStatus === "error"
                ? copy["s4.status.error"]
                : dirty
                  ? copy["s4.status.dirty"]
                  : saveStatus === "saved" && savedAt
                    ? copy["s4.status.saved"](clockOf(savedAt))
                    : ""}
            </span>
          </div>
          {saving && <Progress label={copy["s4.saving.aria"]} />}
          {saveStatus === "error" && (
            <Note tone="error" role="alert">
              {copy["s4.saveError"]}{" "}
              <Button variant="small" onClick={() => void save()}>
                {copy["s4.retry"]}
              </Button>
            </Note>
          )}
        </Card>
      </div>
      <div className="xp-side">
        <Card heading={copy["s4.info.title"]} headingId={infoId}>
          <dl className="xp-dl">
            <dt>{copy["s4.info.started"]}</dt>
            <dd>{formatDateTime(konsultasjon.startetTidspunkt)}</dd>
            <dt>{copy["s4.info.duration"]}</dt>
            <dd>{copy["s4.info.minutes"](minutes)}</dd>
            <dt>{copy["s4.info.doctor"]}</dt>
            <dd>{user?.navn ?? copy["s4.empty.value"]}</dd>
          </dl>
        </Card>
      </div>
      {finishing && (
        <FinishDialog
          patient={patient}
          unsaved={dirty}
          onConfirm={() => void confirmFinish()}
          onCancel={() => setFinishing(false)}
        />
      )}
    </div>
  );
}
