import { useId } from "react";
import { Card } from "./Card";
import { TEXT_SCALES } from "./preferencesStore";
import { copy } from "./copy";
import { useReduceMotion } from "./useReduceMotion";
import { useShell } from "./shellContext";
import { useTextScale } from "./useTextScale";
import { useTheme } from "./useTheme";

const scaleLabels = {
  100: copy["s9.tema.t100"],
  125: copy["s9.tema.t125"],
  150: copy["s9.tema.t150"],
} as const;

const themes = [
  { id: "luna", label: copy["s9.tema.luna"] },
  { id: "klassisk", label: copy["s9.tema.klassisk"] },
] as const;

export function KontrollTemaPage() {
  const { theme, setTheme } = useTheme();
  const { reduceMotion, setReduceMotion } = useReduceMotion();
  const { textScale, setTextScale } = useTextScale();
  const { announce } = useShell();
  const themeId = useId();
  const textId = useId();
  const hintId = useId();

  return (
    <>
      <Card heading={copy["s9.tema.theme"]} headingId={themeId}>
        <div className="xp-radios" role="radiogroup" aria-labelledby={themeId}>
          {themes.map(({ id, label }) => (
            <label key={id} className="xp-check">
              <input
                type="radio"
                name="xp-theme"
                checked={theme === id}
                onChange={() => {
                  setTheme(id);
                  announce(copy["live.theme"](label));
                }}
              />
              {label}
            </label>
          ))}
        </div>
      </Card>
      <Card heading={copy["s9.tema.motion"]}>
        <label className="xp-check">
          <input
            type="checkbox"
            checked={reduceMotion}
            aria-describedby={hintId}
            onChange={(event) => setReduceMotion(event.target.checked)}
          />
          {copy["s9.tema.reduce"]}
        </label>
        <p id={hintId} className="xp-hint">
          {copy["s9.tema.reduceHint"]}
        </p>
      </Card>
      <Card heading={copy["s9.tema.text"]} headingId={textId}>
        <div className="xp-radios" role="radiogroup" aria-labelledby={textId}>
          {TEXT_SCALES.map((scale) => (
            <label key={scale} className="xp-check">
              <input
                type="radio"
                name="xp-text-scale"
                checked={textScale === scale}
                onChange={() => setTextScale(scale)}
              />
              {scaleLabels[scale]}
            </label>
          ))}
        </div>
      </Card>
      <p className="xp-hint">{copy["s9.tema.stored"]}</p>
    </>
  );
}
