import { useId } from "react";
import { XpIcon, type IconName } from "./XpIcon";
import { useShell } from "./shellContext";

type Props = {
  icon: IconName;
  label: string;
  sub?: string;
  badge?: string;
  reason?: string;
  current?: boolean;
  disabled?: boolean;
  stale?: boolean;
  narrowOnly?: boolean;
  onActivate?: () => void;
};

export function TaskLink({
  icon,
  label,
  sub,
  badge,
  reason,
  current,
  disabled,
  stale,
  narrowOnly,
  onActivate,
}: Props) {
  const { setDrawerOpen } = useShell();
  const badgeId = useId();
  const reasonId = useId();
  const describedBy = reason ? reasonId : badge ? badgeId : undefined;
  const className = [
    "xp-tp-link",
    current && "is-current",
    stale && "is-stale",
    narrowOnly && "xp-show-narrow",
  ]
    .filter(Boolean)
    .join(" ");

  function onClick() {
    if (disabled) return;
    setDrawerOpen(false);
    onActivate?.();
  }

  return (
    <>
      <button
        type="button"
        className={className}
        aria-disabled={disabled ? "true" : undefined}
        aria-describedby={disabled ? describedBy : undefined}
        aria-current={current ? "page" : undefined}
        onClick={onClick}
      >
        <XpIcon name={icon} />
        <span className="t">
          {label}
          {sub && <span className="s">{sub}</span>}
        </span>
        {badge && (
          <span className="xp-badge neutral" id={badgeId}>
            {badge}
          </span>
        )}
      </button>
      {reason && (
        <span className="xp-tp-note" id={reasonId}>
          {reason}
        </span>
      )}
    </>
  );
}
