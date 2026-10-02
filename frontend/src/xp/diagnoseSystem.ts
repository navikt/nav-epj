const LABELS: Record<string, string> = {
  ICPC2: "ICPC-2",
  ICD10: "ICD-10",
};

export function systemLabel(system: string) {
  return LABELS[system] ?? system;
}
