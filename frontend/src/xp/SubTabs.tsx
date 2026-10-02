import { useRef, type KeyboardEvent, type ReactNode } from "react";

export type SubTab<T extends string> = {
  id: T;
  label: string;
};

type Props<T extends string> = {
  label: string;
  idPrefix: string;
  tabs: SubTab<T>[];
  selected: T;
  onSelect: (id: T) => void;
  children: ReactNode;
};

export function SubTabs<T extends string>({
  label,
  idPrefix,
  tabs,
  selected,
  onSelect,
  children,
}: Props<T>) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  function move(index: number) {
    const target = tabs[(index + tabs.length) % tabs.length];
    onSelect(target.id);
    refs.current[target.id]?.focus();
  }

  function onKeyDown(event: KeyboardEvent, index: number) {
    if (event.key === "ArrowRight") move(index + 1);
    else if (event.key === "ArrowLeft") move(index - 1);
    else if (event.key === "Home") move(0);
    else if (event.key === "End") move(tabs.length - 1);
    else return;
    event.preventDefault();
  }

  return (
    <>
      <div role="tablist" aria-label={label} className="xp-tablist">
        {tabs.map((tab, index) => (
          <button
            key={tab.id}
            ref={(el) => {
              refs.current[tab.id] = el;
            }}
            type="button"
            role="tab"
            id={`${idPrefix}-tab-${tab.id}`}
            aria-selected={tab.id === selected}
            aria-controls={`${idPrefix}-panel`}
            tabIndex={tab.id === selected ? 0 : -1}
            className="xp-tab"
            onClick={() => onSelect(tab.id)}
            onKeyDown={(event) => onKeyDown(event, index)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`${idPrefix}-panel`}
        aria-labelledby={`${idPrefix}-tab-${selected}`}
        className="xp-tabpanel"
      >
        {children}
      </div>
    </>
  );
}
