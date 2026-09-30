import { useRef, type KeyboardEvent } from "react";
import { Button } from "./Button";
import { copy } from "./copy";
import { XpIcon, type IconName } from "./XpIcon";

type Props = {
  app: string;
  icon: IconName;
  title: string;
  canNavigate: boolean;
  canReload: boolean;
  devOpen: boolean;
  onBack: () => void;
  onForward: () => void;
  onReload: () => void;
  onToggleDev: () => void;
  onPopOut: () => void;
  onClose: () => void;
};

export const APP_FRAME_ID = "app-frame";
export const DEV_PANEL_ID = "dev-panel";

type GlyphProps = {
  glyph: string;
  label: string;
  tooltip: string;
  disabled: boolean;
  onClick: () => void;
};

function GlyphButton({ glyph, label, tooltip, disabled, onClick }: GlyphProps) {
  return (
    <button
      type="button"
      className="xp-iconbtn"
      aria-label={label}
      title={tooltip}
      disabled={disabled}
      onClick={onClick}
    >
      <span aria-hidden="true">{glyph}</span>
    </button>
  );
}

export function AppToolbar({
  app,
  icon,
  title,
  canNavigate,
  canReload,
  devOpen,
  onBack,
  onForward,
  onReload,
  onToggleDev,
  onPopOut,
  onClose,
}: Props) {
  const barRef = useRef<HTMLDivElement>(null);

  function onKeyDown(event: KeyboardEvent) {
    const step =
      event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0 || !barRef.current) return;
    const items = Array.from(
      barRef.current.querySelectorAll<HTMLElement>("button:not([disabled])"),
    );
    const index = items.indexOf(document.activeElement as HTMLElement);
    if (index === -1) return;
    event.preventDefault();
    items[(index + step + items.length) % items.length].focus();
  }

  return (
    <div
      ref={barRef}
      className="xp-toolbar"
      role="toolbar"
      aria-label={copy["s5.toolbar.label"]}
      aria-controls={APP_FRAME_ID}
      onKeyDown={onKeyDown}
    >
      <GlyphButton
        glyph="←"
        label={copy["s5.back"](app)}
        tooltip={copy["s5.back.tooltip"]}
        disabled={!canNavigate}
        onClick={onBack}
      />
      <GlyphButton
        glyph="→"
        label={copy["s5.forward"](app)}
        tooltip={copy["s5.forward.tooltip"]}
        disabled={!canNavigate}
        onClick={onForward}
      />
      <GlyphButton
        glyph="↻"
        label={copy["s5.reload"](app)}
        tooltip={copy["s5.reload.tooltip"]}
        disabled={!canReload}
        onClick={onReload}
      />
      <span className="sep" aria-hidden="true" />
      <XpIcon name={icon} />
      <h1 className="xp-frame-title">{title}</h1>
      <Button
        variant="small"
        aria-pressed={devOpen}
        aria-controls={DEV_PANEL_ID}
        onClick={onToggleDev}
      >
        {copy["s5.dev"]}
      </Button>
      <Button variant="small" disabled={!canReload} onClick={onPopOut}>
        {copy["s5.popout"]}
      </Button>
      <Button variant="small" onClick={onClose}>
        {copy["s5.close"]}
      </Button>
    </div>
  );
}
