import { Provider, UNSAFE_Combobox } from "@navikt/ds-react";
import { nb } from "@navikt/ds-react/locales";
import {
  useEffect,
  useId,
  useMemo,
  useState,
  type FocusEvent,
  type KeyboardEvent,
} from "react";
import { epjDiagnoser } from "@data/diagnoses";
import { copy } from "./copy";
import { diagnoseKey, type DiagnoseItem } from "./journalStore";

const SYSTEM_LABELS: Record<string, string> = {
  ICPC2: "ICPC-2",
  ICD10: "ICD-10",
};

type Props = {
  selected: DiagnoseItem[];
  savedKeys: ReadonlySet<string>;
  disabled?: boolean;
  onAdd: (diagnose: DiagnoseItem) => void;
  onRemove: (code: string, system: string) => void;
};

const catalogue = epjDiagnoser.map((d) => ({
  key: diagnoseKey({ code: d.kode, system: d.diagnosesystem }),
  item: { code: d.kode, system: d.diagnosesystem, text: d.beskrivelse },
  label: `${d.kode} ${d.beskrivelse} · ${SYSTEM_LABELS[d.diagnosesystem]}`,
}));

export function DiagnoseCombobox({
  selected,
  savedKeys,
  disabled,
  onAdd,
  onRemove,
}: Props) {
  const [open, setOpen] = useState(false);
  const inputId = useId();
  const chipsId = useId();

  const options = useMemo(
    () => catalogue.map(({ key, label }) => ({ value: key, label })),
    [],
  );
  const selectedOptions = useMemo(
    () =>
      selected.map((d) => ({
        value: diagnoseKey(d),
        label: `${d.code} ${d.text}`,
      })),
    [selected],
  );

  function onToggleSelected(value: string, isSelected: boolean) {
    const entry = catalogue.find((c) => c.key === value);
    if (!entry) return;
    if (isSelected) onAdd(entry.item);
    else onRemove(entry.item.code, entry.item.system);
  }

  function onKeyDownCapture(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape" && open) {
      event.preventDefault();
      event.stopPropagation();
      setOpen(false);
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      setOpen(true);
    }
  }

  useEffect(() => {
    const list = document.querySelector(
      `#${CSS.escape(`${inputId}-filtered-options`)} [role="listbox"]`,
    );
    list?.setAttribute("aria-label", copy["s4.diag.label"]);
    list?.setAttribute("aria-multiselectable", "true");
  });

  function onBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
  }

  return (
    <div className="xp-field xp-diag">
      <span className="xp-label" aria-hidden="true">
        {copy["s4.diag.label"]}
      </span>
      <span className="xp-hint" aria-hidden="true">
        {copy["s4.diag.hint"]}
      </span>
      <div className="xp-combo-in" onBlur={onBlur}>
        <div
          className="xp-combo"
          onKeyDownCapture={onKeyDownCapture}
          onInput={() => setOpen(true)}
          onClick={(event) => {
            if ((event.target as HTMLElement).closest("input")) setOpen(true);
          }}
        >
          <Provider
            locale={nb}
            translations={{ Combobox: { noMatches: copy["s4.diag.noHits"] } }}
          >
            <UNSAFE_Combobox
              id={inputId}
              label={copy["s4.diag.label"]}
              description={copy["s4.diag.hint"]}
              hideLabel
              isMultiSelect
              isListOpen={open && !disabled}
              disabled={disabled}
              options={options}
              selectedOptions={selectedOptions}
              shouldShowSelectedOptions={false}
              toggleListButton={false}
              placeholder={copy["s4.diag.placeholder"]}
              onToggleSelected={onToggleSelected}
            />
          </Provider>
        </div>
        <button
          type="button"
          className="xp-combo-btn"
          aria-expanded={open}
          aria-controls={`${inputId}-filtered-options`}
          aria-label={open ? copy["s4.diag.hide"] : copy["s4.diag.show"]}
          title={open ? copy["s4.diag.hide"] : copy["s4.diag.show"]}
          disabled={disabled}
          onClick={() => setOpen(!open)}
        >
          <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
            <path
              d={open ? "M2 8l4-4 4 4" : "M2 4l4 4 4-4"}
              stroke="currentColor"
              strokeWidth="2"
              fill="none"
            />
          </svg>
        </button>
      </div>
      {selected.length === 0 ? (
        <p id={chipsId} className="xp-hint">
          {copy["s4.diag.none"]}
        </p>
      ) : (
        <ul id={chipsId} className="xp-chips" aria-label={copy["s4.diag.chips"]}>
          {selected.map((d) => (
            <li
              key={diagnoseKey(d)}
              className={savedKeys.has(diagnoseKey(d)) ? "xp-chip is-saved" : "xp-chip"}
            >
              <code>{d.code}</code>
              <span>{d.text}</span>
              {savedKeys.has(diagnoseKey(d)) && (
                <span>{copy["s4.diag.saved"]}</span>
              )}
              <button
                type="button"
                aria-label={copy["s4.diag.remove"](d.code, d.text)}
                disabled={disabled}
                onClick={() => onRemove(d.code, d.system)}
              >
                <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true" focusable="false">
                  <path d="M1 1l8 8M9 1l-8 8" stroke="currentColor" strokeWidth="1.8" fill="none" />
                </svg>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
