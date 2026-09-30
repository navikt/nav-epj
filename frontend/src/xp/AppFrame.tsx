import { useEffect, useRef } from "react";
import { format, parseISO } from "date-fns";
import { Button } from "./Button";
import { Progress } from "./Progress";
import { appFrameId, appTabId, launchParts } from "./appInfo";
import { useAppRunStore, type AppRun } from "./appRunStore";
import { copy } from "./copy";
import { dismissTimeoutDialog, reportTimeout } from "./launchApp";
import { fullName } from "./patientInfo";

export const FRAME_TIMEOUT_MS = 8000;

export const IFRAME_SANDBOX =
  "allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads";
export const IFRAME_REFERRER_POLICY = "no-referrer";

type Props = {
  run: AppRun;
  stale: boolean;
  otherPatientName: string;
  onRestart: () => void;
  onPopOut: () => void;
  onClose: () => void;
  onOpenJournal: () => void;
};

function Overlay({
  title,
  body,
  buttons,
  live,
}: {
  title: string;
  body: string;
  buttons: { label: string; onClick: () => void; isDefault?: boolean }[];
  live?: "alert";
}) {
  return (
    <div className="xp-frame-over">
      <section className="xp-card" role={live}>
        <h2>{title}</h2>
        <p>{body}</p>
        <div className="xp-form-actions">
          {buttons.map((button) => (
            <Button
              key={button.label}
              isDefault={button.isDefault}
              onClick={button.onClick}
            >
              {button.label}
            </Button>
          ))}
        </div>
      </section>
    </div>
  );
}

export function AppFrame({
  run,
  stale,
  otherPatientName,
  onRestart,
  onPopOut,
  onClose,
  onOpenJournal,
}: Props) {
  const { clientId, launchUrl, status, attempt } = run;
  const parts = launchUrl ? launchParts(launchUrl) : null;
  const waiting = launchUrl !== null && status === "starting" && !stale;
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!waiting || !launchUrl) return;
    const origin = launchParts(launchUrl).origin;
    const timer = setTimeout(
      () => reportTimeout(clientId, origin),
      FRAME_TIMEOUT_MS,
    );
    return () => clearTimeout(timer);
  }, [waiting, clientId, launchUrl, attempt]);

  useEffect(() => {
    const container = containerRef.current;
    if (!stale || !container || container.closest("[hidden]")) return;
    const focused = document.activeElement;
    if (
      focused === null ||
      focused === document.body ||
      focused === container.querySelector("iframe")
    ) {
      container.querySelector<HTMLElement>("button.is-default")?.focus();
    }
  }, [stale]);

  function onLoad() {
    const store = useAppRunStore.getState();
    const current = store.runs.find((r) => r.clientId === clientId);
    if (!current || !parts) return;
    if (current.status === "starting" || current.status === "timeout") {
      store.setStatus(clientId, "running");
      dismissTimeoutDialog(clientId);
    }
    store.addEvent(clientId, { kind: "load", url: parts.origin });
  }

  const patientName = fullName(run.patient);
  const showFrame =
    launchUrl !== null && status !== "session" && status !== "error";

  let overlay = null;
  if (stale) {
    overlay = (
      <Overlay
        title={copy["s5.stale.title"](patientName, otherPatientName)}
        body={copy["s5.stale.body"]}
        buttons={[
          { label: copy["s5.close"], onClick: onClose },
          {
            label: copy["s5.stale.open"](otherPatientName),
            onClick: onOpenJournal,
            isDefault: true,
          },
        ]}
      />
    );
  } else if (status === "session") {
    overlay = (
      <Overlay
        title={copy["s5.session.title"]}
        body={copy["s5.session.body"]}
        live="alert"
        buttons={[
          {
            label: copy["s5.session.reload"],
            onClick: () => window.location.reload(),
            isDefault: true,
          },
        ]}
      />
    );
  } else if (status === "error") {
    overlay = (
      <Overlay
        title={copy["s5.error.title"]}
        body={copy["s5.error.body"](run.navn)}
        live="alert"
        buttons={[
          {
            label: copy["s5.error.restart"],
            onClick: onRestart,
            isDefault: true,
          },
          { label: copy["s5.popout"], onClick: onPopOut },
          { label: copy["s5.close"], onClick: onClose },
        ]}
      />
    );
  } else if (status === "timeout") {
    overlay = (
      <Overlay
        title={copy["s6.timeout.title"]}
        body={copy["s6.timeout.body"](run.navn)}
        buttons={[
          { label: copy["s5.popout"], onClick: onPopOut, isDefault: true },
          { label: copy["s5.close"], onClick: onClose },
        ]}
      />
    );
  } else if (status === "starting") {
    const time = format(parseISO(run.konsultasjon.startetTidspunkt), "HH:mm");
    overlay = (
      <div className="xp-frame-over">
        <section className="xp-card" role="status">
          <h2>{copy["s6.title"](run.navn)}</h2>
          <ul className="xp-steps">
            <li>
              <span aria-hidden="true">{parts ? "✔" : "◐"}</span>{" "}
              {copy["s6.step1"]}{" "}
              {parts
                ? copy["s6.step1.ok"](patientName, time)
                : copy["s6.step1.busy"]}
            </li>
            <li>
              <span aria-hidden="true">{parts ? "◐" : "○"}</span>{" "}
              {copy["s6.step2"]}
              {parts && ` ${copy["s6.step2.wait"](parts.origin)}`}
            </li>
          </ul>
          <Progress label={copy["s6.title"](run.navn)} />
          <p>{copy["s6.hint"]}</p>
        </section>
      </div>
    );
  }

  return (
    <div className="xp-frame" id={appFrameId(clientId)} ref={containerRef}>
      {showFrame && launchUrl && (
        <iframe
          key={`${appTabId(clientId)}:${attempt}`}
          title={copy["s5.iframeTitle"](run.navn, clientId, patientName)}
          src={launchUrl}
          sandbox={IFRAME_SANDBOX}
          referrerPolicy={IFRAME_REFERRER_POLICY}
          allow=""
          inert={stale}
          aria-hidden={stale || undefined}
          onLoad={onLoad}
        />
      )}
      {overlay}
    </div>
  );
}
