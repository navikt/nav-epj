import { copy } from "./copy";
import type { AppRun, RunStatus } from "./appRunStore";
import { fullName } from "./patientInfo";
import type { IconName } from "./XpIcon";

const APP_ICONS: readonly IconName[] = [
  "app",
  "journal",
  "mappe",
  "sykmelding",
  "timebok",
  "validator",
  "vindu",
];

export function appIconName(ikon: string): IconName {
  return APP_ICONS.find((name) => name === ikon) ?? "vindu";
}

export function initialsOf(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
}

export const APP_TAB_PREFIX = "app:";

export function appTabId(clientId: string) {
  return `${APP_TAB_PREFIX}${clientId}`;
}

export const appFrameId = (clientId: string) => `app-frame-${clientId}`;

export const devPanelId = (clientId: string) => `dev-panel-${clientId}`;

export function launchParts(launchUrl: string) {
  try {
    const url = new URL(launchUrl);
    return {
      origin: url.origin,
      launchId: url.searchParams.get("launch") ?? "",
      iss: url.searchParams.get("iss") ?? "",
    };
  } catch {
    return { origin: "", launchId: "", iss: "" };
  }
}

export function statusText(status: RunStatus, stale: boolean, run: AppRun) {
  if (stale) return copy["s5.status.stale"](fullName(run.patient));
  switch (status) {
    case "starting":
      return copy["s5.status.starting"];
    case "running":
      return copy["pane.apps.runningHost"];
    case "timeout":
      return copy["s5.status.timeout"];
    case "error":
      return copy["s5.status.error"];
    case "session":
      return copy["s5.status.session"];
  }
}
