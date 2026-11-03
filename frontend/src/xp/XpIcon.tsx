const urls = import.meta.glob<string>("./icons/*.svg", {
  eager: true,
  query: "?url",
  import: "default",
});

export type IconName =
  | "app"
  | "appointment-book"
  | "coming-soon"
  | "control-panel"
  | "error"
  | "event-log"
  | "folder"
  | "help"
  | "home"
  | "info"
  | "journal"
  | "log-out"
  | "new-tab"
  | "newpatient"
  | "patient"
  | "patients"
  | "question"
  | "recent"
  | "save"
  | "search"
  | "sick-note"
  | "systeminfo"
  | "user"
  | "validator"
  | "warning"
  | "window";

export type IconSize = 16 | 32 | 48;

type Props = { name: IconName; size?: IconSize };

export function XpIcon({ name, size = 16 }: Props) {
  return (
    <img
      src={urls[`./icons/${name}-${size}.svg`]}
      width={size}
      height={size}
      alt=""
      aria-hidden="true"
    />
  );
}
