import { useEffect, useRef, useState, type ReactNode } from "react";
import { copy } from "./copy";
import { XpIcon, type IconName } from "./XpIcon";

const AUTO_HIDE_MS = 6000;

type Props = {
  title: string;
  children: ReactNode;
  icon?: IconName;
  duration?: number;
  onClose: () => void;
};

export function BalloonTip({
  title,
  children,
  icon = "info",
  duration = AUTO_HIDE_MS,
  onClose,
}: Props) {
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const onCloseRef = useRef(onClose);
  const paused = hovered || focused;

  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (paused) return;
    const id = setTimeout(() => onCloseRef.current(), duration);
    return () => clearTimeout(id);
  }, [paused, duration]);

  return (
    <div
      className="xp-balloon is-docked"
      role="status"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) {
          setFocused(false);
        }
      }}
    >
      <XpIcon name={icon} size={32} />
      <div className="body">
        <b>{title}</b>
        {children}
      </div>
      <button
        type="button"
        className="x"
        aria-label={copy["balloon.close"]}
        onClick={onClose}
      >
        <svg
          width="10"
          height="10"
          viewBox="0 0 10 10"
          aria-hidden="true"
          focusable="false"
        >
          <path
            d="M1 1l8 8M9 1l-8 8"
            stroke="currentColor"
            strokeWidth="1.8"
            fill="none"
          />
        </svg>
      </button>
    </div>
  );
}
