import { copy } from "./copy";
import { TaskPanel } from "./TaskPanel";
import { TEXT_SCALES } from "./preferencesStore";
import { useTheme } from "./useTheme";
import { useReduceMotion } from "./useReduceMotion";
import { useTextScale } from "./useTextScale";
import { useShell } from "./shellContext";

const scaleLabels = {
  100: copy["s9.tema.t100"],
  125: copy["s9.tema.t125"],
  150: copy["s9.tema.t150"],
} as const;

export function AppearancePanel() {
  const { theme, setTheme } = useTheme();
  const { reduceMotion, setReduceMotion } = useReduceMotion();
  const { textScale, setTextScale } = useTextScale();
  const { announce } = useShell();

  function onThemeChange(klassisk: boolean) {
    setTheme(klassisk ? "klassisk" : "luna");
    announce(
      copy["live.theme"](
        klassisk ? copy["s9.tema.klassisk"] : copy["s9.tema.luna"],
      ),
    );
  }

  return (
    <TaskPanel title={copy["s9.tema.theme"]}>
      <label className="xp-check">
        <input
          type="checkbox"
          checked={theme === "klassisk"}
          onChange={(event) => onThemeChange(event.target.checked)}
        />
        {copy["s9.tema.klassisk"]}
      </label>
      <label className="xp-check">
        <input
          type="checkbox"
          checked={reduceMotion}
          onChange={(event) => setReduceMotion(event.target.checked)}
        />
        {copy["s9.tema.reduce"]}
      </label>
      <fieldset className="xp-fieldset">
        <legend>{copy["s9.tema.text"]}</legend>
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
      </fieldset>
    </TaskPanel>
  );
}
