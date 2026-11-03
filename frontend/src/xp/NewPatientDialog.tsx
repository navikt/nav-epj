import { useRef, useState, type FormEvent } from "react";
import { Badge } from "./Badge";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { Note } from "./Note";
import { Progress } from "./Progress";
import { TextField } from "./TextField";
import { createPatient } from "./api";
import { useBalloonStore } from "./balloonStore";
import { copy } from "./copy";
import { useCurrentUser } from "./currentUser";
import { derivePersonident } from "./patientInfo";
import { usePatientsStore } from "./patientsStore";

type Errors = Partial<Record<"givenName" | "familyName" | "fnr", string>>;

type Props = {
  onClose: () => void;
};

function validate(givenName: string, familyName: string, fnr: string): Errors {
  const errors: Errors = {};
  if (!givenName.trim()) errors.givenName = copy["s3b.err.givenName"];
  if (!familyName.trim()) errors.familyName = copy["s3b.err.familyName"];
  if (!/^\d{11}$/.test(fnr) || !derivePersonident(fnr)) {
    errors.fnr = copy["s3b.err.fnr"];
  }
  return errors;
}

export function NewPatientDialog({ onClose }: Props) {
  const user = useCurrentUser();
  const [givenName, setGivenName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [fnr, setFnr] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState(false);
  const givenNameRef = useRef<HTMLInputElement>(null);
  const familyNameRef = useRef<HTMLInputElement>(null);
  const fnrRef = useRef<HTMLInputElement>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving) return;
    const found = validate(givenName, familyName, fnr.trim());
    setErrors(found);
    setServerError(false);
    if (found.givenName) return givenNameRef.current?.focus();
    if (found.familyName) return familyNameRef.current?.focus();
    if (found.fnr) return fnrRef.current?.focus();

    const derived = derivePersonident(fnr.trim());
    if (!derived) return;
    setSaving(true);
    try {
      await createPatient({
        givenName: givenName.trim(),
        familyName: familyName.trim(),
        personident: fnr.trim(),
        ...derived,
      });
    } catch {
      setSaving(false);
      setServerError(true);
      return;
    }
    useBalloonStore.getState().show({
      title: copy["s3b.saved.title"],
      body: copy["s3b.saved.body"],
    });
    void usePatientsStore.getState().load();
    onClose();
  }

  return (
    <Dialog title={copy["s3b.title"]} onClose={saving ? () => {} : onClose}>
      <form className="xp-msg" noValidate onSubmit={submit}>
        <div className="xp-msg-main">
          <div className="xp-pagehead">
            <p>{copy["s3b.intro"](user?.name ?? "")}</p>
            <Badge tone="info">{copy["badge.localOnly"]}</Badge>
          </div>
          <TextField
            label={copy["s3b.givenName"]}
            value={givenName}
            error={errors.givenName}
            disabled={saving}
            autoComplete="off"
            data-autofocus=""
            inputRef={givenNameRef}
            onChange={(event) => setGivenName(event.target.value)}
          />
          <TextField
            label={copy["s3b.familyName"]}
            value={familyName}
            error={errors.familyName}
            disabled={saving}
            autoComplete="off"
            inputRef={familyNameRef}
            onChange={(event) => setFamilyName(event.target.value)}
          />
          <TextField
            label={copy["s3b.fnr"]}
            hint={copy["s3b.fnr.hint"]}
            value={fnr}
            error={errors.fnr}
            disabled={saving}
            inputMode="numeric"
            autoComplete="off"
            spellCheck={false}
            inputRef={fnrRef}
            onChange={(event) => setFnr(event.target.value)}
          />
          {serverError && (
            <Note tone="error" role="alert">
              {copy["s3b.err.server"]}
            </Note>
          )}
          {saving && (
            <>
              <Progress label={copy["s3b.savingStatus"]} />
              <p role="status">{copy["s3b.savingStatus"]}</p>
            </>
          )}
          <div className="xp-msg-actions">
            <Button type="submit" isDefault disabled={saving}>
              {saving ? copy["s3b.saving"] : copy["s3b.save"]}
            </Button>
            <Button onClick={onClose} disabled={saving}>
              {copy["s3b.cancel"]}
            </Button>
          </div>
        </div>
      </form>
    </Dialog>
  );
}
