import { copy } from "./copy";

export type ScopeGroupId = "identity" | "launch" | "patient" | "offline";

const IDENTITY = new Set(["openid", "profile", "fhirUser"]);
const LAUNCH = new Set(["launch", "launch/patient"]);

export const SCOPE_GROUP_LABELS = {
  identity: copy["s10.group.identity"],
  launch: copy["s10.group.launch"],
  patient: copy["s10.group.patient"],
  offline: copy["s10.group.offline"],
} as const;

const GROUP_ORDER: readonly ScopeGroupId[] = [
  "identity",
  "launch",
  "patient",
  "offline",
];

export function scopeGroupOf(scope: string): ScopeGroupId {
  if (IDENTITY.has(scope)) return "identity";
  if (LAUNCH.has(scope)) return "launch";
  if (scope === "offline_access") return "offline";
  return "patient";
}

export function explanationOf(scope: string): string | undefined {
  const key = `scope.${scope}`;
  if (!Object.hasOwn(copy, key)) return undefined;
  const text: unknown = Reflect.get(copy, key);
  return typeof text === "string" ? text : undefined;
}

export function groupScopes(scopes: readonly string[]) {
  return GROUP_ORDER.map((id) => ({
    id,
    label: SCOPE_GROUP_LABELS[id],
    scopes: scopes.filter((scope) => scopeGroupOf(scope) === id),
  })).filter((group) => group.scopes.length > 0);
}
