import { useEffect, useRef, type KeyboardEvent } from "react";
import { copy } from "./copy";
import { XpIcon } from "./XpIcon";
import { TestMarker } from "./TestMarker";
import { isModalOpen } from "./modalStore";
import { usePatientsStore } from "./patientsStore";
import {
  MENU_BUTTON_ID,
  SEARCH_INPUT_ID,
  TASK_PANE_ID,
  useShell,
} from "./shellContext";

export type HeaderUser = {
  navn: string;
  autorisasjon: string;
  legekontor: string;
};

type Props = {
  user: HeaderUser | null;
  onLogout: () => void;
  onSearchSubmit: () => void;
};

export function AppHeader({ user, onLogout, onSearchSubmit }: Props) {
  const { narrow, drawerOpen, setDrawerOpen } = useShell();
  const query = usePatientsStore((s) => s.query);
  const setQuery = usePatientsStore((s) => s.setQuery);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    function onKeyDown(event: globalThis.KeyboardEvent) {
      if (
        !isModalOpen() &&
        event.ctrlKey &&
        event.shiftKey &&
        !event.altKey &&
        event.key?.toLowerCase() === "p"
      ) {
        event.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  function onInputKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape" && query !== "") {
      event.preventDefault();
      setQuery("");
    } else if (event.key === "Enter") {
      event.preventDefault();
      onSearchSubmit();
    }
  }

  return (
    <header className="xp-header" data-xp-landmark="header" tabIndex={-1}>
      <button
        type="button"
        id={MENU_BUTTON_ID}
        className="xp-hbtn xp-show-narrow"
        aria-expanded={drawerOpen}
        aria-controls={TASK_PANE_ID}
        onClick={() => setDrawerOpen(!drawerOpen)}
      >
        {copy["header.menu"]}
      </button>
      <div className="xp-brand">
        <XpIcon name="app" size={32} />
        <b>{copy["app.name"]}</b>
        <span className="xp-hide-narrow">{copy["app.subtitle"]}</span>
      </div>
      <div className="xp-hsearch" role="search">
        <label htmlFor={SEARCH_INPUT_ID} className="sr-only">
          {copy["header.search.label"]}
        </label>
        <XpIcon name="sok" />
        <input
          ref={inputRef}
          id={SEARCH_INPUT_ID}
          type="search"
          autoComplete="off"
          value={query}
          placeholder={
            narrow
              ? copy["header.search.placeholderNarrow"]
              : copy["header.search.placeholder"]
          }
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={onInputKeyDown}
        />
      </div>
      <span className="xp-hspacer" />
      <TestMarker />
      {user && (
        <span className="xp-user xp-hide-narrow">
          {copy["header.user"](user.navn, user.autorisasjon, user.legekontor)}
        </span>
      )}
      <button
        type="button"
        className="xp-hbtn xp-hide-narrow"
        onClick={onLogout}
      >
        <XpIcon name="loggav" />
        {copy["header.logout"]}
      </button>
    </header>
  );
}
