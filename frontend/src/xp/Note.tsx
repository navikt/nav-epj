import type { ReactNode } from "react";
import { XpIcon, type IconName } from "./XpIcon";

type Tone = "warn" | "error" | "info" | "ok";

const ICONS: Record<Tone, IconName> = {
  warn: "advarsel",
  error: "feil",
  info: "info",
  ok: "info",
};

type Props = {
  tone: Tone;
  role?: "status" | "alert";
  children: ReactNode;
};

export function Note({ tone, role, children }: Props) {
  return (
    <div className={`xp-note ${tone}`} role={role}>
      <XpIcon name={ICONS[tone]} size={16} />
      <span>{children}</span>
    </div>
  );
}
