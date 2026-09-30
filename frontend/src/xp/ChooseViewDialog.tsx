import { useId, useState } from "react";
import { Button } from "./Button";
import { Dialog } from "./Dialog";
import { copy } from "./copy";
import { useJournalStore } from "./journalStore";
import { useLaunchModeStore, type LaunchChoice } from "./launchModeStore";
import { fullName } from "./patientInfo";
import { useStartApp } from "./useStartApp";
import type { App } from "../utils/mapping/epj";

type Props = {
  app: App;
  onClose: () => void;
};

export function ChooseViewDialog({ app, onClose }: Props) {
  const patient = useJournalStore((s) => s.patient);
  const { start } = useStartApp();
  const [choice, setChoice] = useState<LaunchChoice>("iframe");
  const [remember, setRemember] = useState(false);
  const questionId = useId();
  const hintId = useId();

  function submit() {
    if (remember) useLaunchModeStore.getState().remember(app.clientId, choice);
    onClose();
    void start(app, choice);
  }

  return (
    <Dialog
      title={copy["s6b.title"](app.navn)}
      labelledBy={questionId}
      describedBy={hintId}
      onClose={onClose}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        <p id={questionId}>
          {copy["s6b.question"](app.navn, patient ? fullName(patient) : "")}
        </p>
        <fieldset className="xp-fieldset">
          <legend>{copy["s6b.legend"]}</legend>
          <div className="xp-radios">
            <label className="xp-check">
              <input
                type="radio"
                name="visning"
                checked={choice === "iframe"}
                onChange={() => setChoice("iframe")}
                data-autofocus
              />
              {copy["s6b.iframe"]}
            </label>
            <label className="xp-check">
              <input
                type="radio"
                name="visning"
                checked={choice === "tab"}
                onChange={() => setChoice("tab")}
              />
              {copy["s6b.tab"]}
            </label>
          </div>
        </fieldset>
        <label className="xp-check">
          <input
            type="checkbox"
            checked={remember}
            onChange={(event) => setRemember(event.target.checked)}
          />
          {copy["s6b.remember"]}
        </label>
        <p id={hintId}>{copy["s6b.rememberHint"]}</p>
        <div className="xp-msg-actions">
          <Button type="submit" isDefault>
            {copy["s6b.start"]}
          </Button>
          <Button onClick={onClose}>{copy["common.cancel"]}</Button>
        </div>
      </form>
    </Dialog>
  );
}
