import type { ReactNode } from "react";

type Props = {
  tone?: "ok" | "warn" | "error" | "info" | "neutral";
  children: ReactNode;
};

export function Badge({ tone = "neutral", children }: Props) {
  return <span className={`xp-badge ${tone}`}>{children}</span>;
}
