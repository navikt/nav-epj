import { Fragment, useId } from "react";
import { parseISO } from "date-fns";
import { Button } from "./Button";
import { IFRAME_REFERRER_POLICY, IFRAME_SANDBOX } from "./AppFrame";
import { devPanelId, launchParts } from "./appInfo";
import { accessExpiry, type AppRun } from "./appRunStore";
import { copyText } from "./clipboard";
import { copy } from "./copy";
import { formatTime, fullName } from "./patientInfo";

type Props = {
  run: AppRun;
  hidden?: boolean;
  onClose: () => void;
};

export function DevPanel({ run, hidden, onClose }: Props) {
  const headingId = useId();
  const parts = run.launchUrl
    ? launchParts(run.launchUrl)
    : { origin: "", launchId: "", iss: "" };
  const rows: [string, string][] = [
    [copy["dev.clientId"], run.clientId],
    [copy["dev.launchUrl"], run.launchUrl ?? "–"],
    [
      copy["dev.launchId"],
      parts.launchId ? copy["dev.launchId.value"](parts.launchId) : "–",
    ],
    [copy["dev.iss"], parts.iss || "–"],
    [copy["dev.patient"], `Patient/${run.patient.id}`],
    [copy["dev.encounter"], `Encounter/${run.konsultasjon.id}`],
    [copy["dev.started"], formatTime(run.startedAt)],
    [copy["dev.expires"], formatTime(accessExpiry(run.startedAt))],
    [copy["dev.sandbox"], IFRAME_SANDBOX],
    [copy["dev.referrer"], IFRAME_REFERRER_POLICY],
  ];
  const events = run.events.map((event) => {
    const at = formatTime(event.at, true);
    return {
      id: event.id,
      text:
        event.kind === "launch"
          ? copy["dev.event.launch"](at, event.status)
          : copy["dev.event.load"](at, event.url),
    };
  });

  function copyAll() {
    const text = [
      `${run.navn} (${fullName(run.patient)}, ${formatTime(parseISO(run.konsultasjon.startetTidspunkt))})`,
      ...rows.map(([key, value]) => `${key}: ${value}`),
      ...events.map((event) => event.text),
    ].join("\n");
    void copyText(text, copy["dev.copied"]);
  }

  return (
    <section
      id={devPanelId(run.clientId)}
      className="xp-devpanel"
      hidden={hidden}
      aria-labelledby={headingId}
    >
      <div className="xp-devpanel-head">
        <h2 id={headingId}>{copy["dev.title"](run.navn)}</h2>
        <Button variant="small" onClick={copyAll}>
          {copy["dev.copy"]}
        </Button>
        <Button
          variant="small"
          aria-label={copy["dev.close"]}
          onClick={onClose}
        >
          {copy["common.close"]}
        </Button>
      </div>
      <div className="xp-devpanel-cols">
        <dl className="xp-dl">
          {rows.map(([key, value]) => (
            <Fragment key={key}>
              <dt>{key}</dt>
              <dd className="mono">{value}</dd>
            </Fragment>
          ))}
        </dl>
        <div>
          <h3 className="xp-h2">{copy["dev.events"]}</h3>
          <ul className="xp-events">
            {events.map((event) => (
              <li key={event.id}>{event.text}</li>
            ))}
          </ul>
        </div>
      </div>
      <p>{copy["dev.devtoolsNote"](parts.origin)}</p>
    </section>
  );
}
