import { copy } from "./copy";
import { formatDateTime } from "./patientInfo";
import type {
  CapabilityStatement,
  Session,
  SmartConfiguration,
} from "../utils/mapping/epj";

export type Row = readonly [label: string, value: string];

export type SessionUser = { navn: string; hpr: string };

export const HPR_CLAIM = "helseid://claims/hpr/hpr_number";
const PID_CLAIM = "helseid://claims/identity/pid";
const EMPTY = copy["s4.empty.value"];
const FIXED_CLAIMS = new Set(["iss", "aud", "name", HPR_CLAIM]);

export function fhirBaseOf(smart: SmartConfiguration) {
  return `${new URL(smart.authorization_endpoint).origin}/fhir`;
}

function dateTime(iso: string | null | undefined) {
  return iso ? formatDateTime(iso) : EMPTY;
}

function expiryText(iso: string | null | undefined, now: Date) {
  if (!iso) return EMPTY;
  const [dato, tid] = formatDateTime(iso).split(" ");
  const remaining = new Date(iso).getTime() - now.getTime();
  if (remaining <= 0) return `${dato} ${tid} (${copy["s5.status.session"]})`;
  return copy["s11.helseid.expValue"](
    dato,
    tid,
    Math.round(remaining / 60_000),
  );
}

export function helseIdRows(
  session: Session,
  user: SessionUser | null,
  now: Date,
): Row[] {
  const local = session.idp === "local-stub";
  return [
    [copy["s11.helseid.user"], session.claims.name ?? user?.navn ?? EMPTY],
    [copy["s11.helseid.hpr"], session.claims[HPR_CLAIM] ?? user?.hpr ?? EMPTY],
    [
      copy["s11.helseid.idp"],
      local ? copy["s11.helseid.idp.local"] : copy["s11.helseid.idp.value"],
    ],
    [copy["s11.helseid.iat"], dateTime(session.issuedAt)],
    [copy["s11.helseid.exp"], expiryText(session.expiresAt, now)],
  ];
}

export function claimRows(session: Session): Row[] {
  const { claims } = session;
  const extra = Object.keys(claims)
    .filter((key) => !FIXED_CLAIMS.has(key) && key !== PID_CLAIM)
    .map((key): Row => [key, claims[key]]);
  return [
    ["iss", claims.iss ?? EMPTY],
    ["aud", claims.aud ?? EMPTY],
    ["name", claims.name ?? EMPTY],
    ["hpr_number", claims[HPR_CLAIM] ?? EMPTY],
    ["pid", copy["s11.claims.hidden"]],
    ["iat", session.issuedAt ?? EMPTY],
    ["exp", session.expiresAt ?? EMPTY],
    ...extra,
  ];
}

export function smartRows(smart: SmartConfiguration): Row[] {
  const fhirBase = fhirBaseOf(smart);
  const contexts = smart.capabilities.filter((c) => c.startsWith("context-"));
  return [
    [copy["s11.smart.iss"], fhirBase],
    [
      copy["s11.smart.discovery"],
      `${fhirBase}/.well-known/smart-configuration`,
    ],
    [copy["s11.smart.authorize"], smart.authorization_endpoint],
    [copy["s11.smart.token"], smart.token_endpoint],
    [copy["s11.smart.jwks"], smart.jwks_uri],
    [copy["s11.smart.launch"], copy["s11.smart.launch.value"]],
    [
      copy["s11.smart.clientAuth"],
      smart.token_endpoint_auth_methods_supported.join(", "),
    ],
    [copy["s11.smart.accessToken"], copy["s11.smart.accessToken.value"]],
    [copy["s11.smart.context"], contexts.join(", ") || EMPTY],
  ];
}

export function fhirRows(
  statement: CapabilityStatement,
  fhirBase: string | null,
): Row[] {
  const version = statement.fhirVersion.startsWith("4.0")
    ? `${statement.fhirVersion} (R4)`
    : statement.fhirVersion;
  return [
    [copy["s11.fhir.version"], version],
    [copy["s11.fhir.capability"], fhirBase ? `${fhirBase}/metadata` : EMPTY],
  ];
}

export function resourceRows(statement: CapabilityStatement): Row[] {
  return (statement.rest ?? [])
    .flatMap((rest) => rest.resource ?? [])
    .map((resource): Row => [
      resource.type,
      (resource.interaction ?? []).map((i) => i.code).join(", ") || EMPTY,
    ]);
}

type Section = { title: string; rows: Row[] };

export function buildSummary(sections: Section[]) {
  return [
    copy["s11.title"],
    ...sections.flatMap(({ title, rows }) => [
      "",
      title,
      ...rows.map(([label, value]) => `${label}: ${value}`),
    ]),
  ].join("\n");
}
