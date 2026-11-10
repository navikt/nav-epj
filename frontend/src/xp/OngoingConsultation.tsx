import { useId, useMemo, useState } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { CancelDialog } from "./CancelDialog";
import { Card } from "./Card";
import { ConsultationDocuments } from "./ConsultationDocuments";
import { DiagnosisCombobox } from "./DiagnosisCombobox";
import { FinishDialog } from "./FinishDialog";
import { Note } from "./Note";
import { Progress } from "./Progress";
import { TextArea } from "./TextArea";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";
import { diagnosisKey, isDirty, lacksMainDiagnosis, useJournalStore } from "./journalStore";
import { formatDateTime, formatTime, minutesSince } from "./patientInfo";
import { useShell } from "./shellContext";
import { useNow } from "./useNow";
import type { Consultation, Patient } from "../utils/mapping/ehr";

type Props = {
  patient: Patient;
  consultation: Consultation;
};

export function OngoingConsultation({ patient, consultation }: Props) {
  const titleId = useId();
  const infoId = useId();
  const user = useCurrentUser();
  const { announce } = useShell();
  const now = useNow();
  const [finishing, setFinishing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const draft = useJournalStore((s) => s.draft);
  const baseline = useJournalStore((s) => s.baseline);
  const saveStatus = useJournalStore((s) => s.saveStatus);
  const savedAt = useJournalStore((s) => s.savedAt);
  const dirty = isDirty({ draft, baseline });
  const saving = saveStatus === "saving";
  const missingMain = lacksMainDiagnosis(draft);
  const minutes = minutesSince(consultation.startedAt, now);
  const savedMainKeys = useMemo(
    () => new Set(baseline.mainDiagnosis ? [diagnosisKey(baseline.mainDiagnosis)] : []),
    [baseline.mainDiagnosis],
  );
  const savedSecondaryKeys = useMemo(
    () => new Set(baseline.secondaryDiagnoses.map(diagnosisKey)),
    [baseline.secondaryDiagnoses],
  );
  const {
    setMainDiagnosis,
    removeMainDiagnosis,
    addSecondaryDiagnosis,
    removeSecondaryDiagnosis,
    setClinicalNote,
    save,
    cancel,
  } = useJournalStore.getState();

  async function confirmFinish() {
    setFinishing(false);
    const ok = await save({ finalize: true });
    if (ok) announce(copy["live.consultationDone"]);
  }

  async function confirmCancel() {
    setCancelling(false);
    const ok = await cancel();
    if (ok) announce(copy["live.consultationCancelled"]);
  }

  return (
    <div className="xp-cols">
      <div className="xp-grow">
        <Card heading={copy["s4.ongoing.title"]} headingId={titleId}>
          <div>
            <Badge tone="info">{copy["s4.ongoing.badge"](minutes)}</Badge>
          </div>
          <DiagnosisCombobox
            label={copy["s4.diag.main.label"]}
            chipsLabel={copy["s4.diag.main.chips"]}
            noneText={copy["s4.diag.main.none"]}
            selected={draft.mainDiagnosis ? [draft.mainDiagnosis] : []}
            savedKeys={savedMainKeys}
            disabled={saving}
            onAdd={setMainDiagnosis}
            onRemove={removeMainDiagnosis}
          />
          <DiagnosisCombobox
            multiple
            label={copy["s4.diag.secondary.label"]}
            chipsLabel={copy["s4.diag.secondary.chips"]}
            noneText={copy["s4.diag.secondary.none"]}
            excludedKey={
              draft.mainDiagnosis ? diagnosisKey(draft.mainDiagnosis) : undefined
            }
            selected={draft.secondaryDiagnoses}
            savedKeys={savedSecondaryKeys}
            disabled={saving}
            onAdd={addSecondaryDiagnosis}
            onRemove={removeSecondaryDiagnosis}
          />
          {missingMain && (
            <Note tone="error" role="alert">
              {copy["s4.diag.mainRequired"]}
            </Note>
          )}
          <TextArea
            label={copy["s4.note.label"]}
            rows={6}
            value={draft.clinicalNote}
            readOnly={saving}
            onChange={(event) => setClinicalNote(event.target.value)}
          />
          <ConsultationDocuments consultation={consultation} />
          <div className="xp-form-actions">
            <Button
              isDefault
              aria-disabled={saving || missingMain}
              onClick={() => void save()}
            >
              {saving ? copy["s4.saving"] : copy["s4.save"]}
            </Button>
            <Button
              aria-disabled={saving || missingMain}
              onClick={() => {
                if (!saving && !missingMain) setFinishing(true);
              }}
            >
              {copy["s4.finish"]}
            </Button>
            <Button
              aria-disabled={saving}
              onClick={() => {
                if (!saving) setCancelling(true);
              }}
            >
              {copy["s4.cancelConsultation"]}
            </Button>
            <span className="xp-status-text" role="status">
              {saveStatus === "error"
                ? copy["s4.status.error"]
                : dirty
                  ? copy["s4.status.dirty"]
                  : saveStatus === "saved" && savedAt
                    ? copy["s4.status.saved"](formatTime(savedAt))
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
            <dd>{formatDateTime(consultation.startedAt)}</dd>
            <dt>{copy["s4.info.duration"]}</dt>
            <dd>{copy["s4.info.minutes"](minutes)}</dd>
            <dt>{copy["s4.info.doctor"]}</dt>
            <dd>{user?.name ?? copy["s4.empty.value"]}</dd>
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
      {cancelling && (
        <CancelDialog
          patient={patient}
          unsaved={dirty}
          onConfirm={() => void confirmCancel()}
          onCancel={() => setCancelling(false)}
        />
      )}
    </div>
  );
}
