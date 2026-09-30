const urls = import.meta.glob<string>("./icons/*.svg", {
  eager: true,
  query: "?url",
  import: "default",
});

export type IconName =
  | "advarsel"
  | "app"
  | "bruker"
  | "feil"
  | "hendelseslogg"
  | "hjelp"
  | "hjem"
  | "info"
  | "journal"
  | "kommer"
  | "kontrollpanel"
  | "lagre"
  | "loggav"
  | "mappe"
  | "ny-fane"
  | "nylig"
  | "nypasient"
  | "pasient"
  | "pasienter"
  | "sok"
  | "sporsmal"
  | "sykmelding"
  | "systeminfo"
  | "timebok"
  | "validator"
  | "vindu";

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
