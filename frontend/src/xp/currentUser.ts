import { createContext, use } from "react";

export type CurrentUser = {
  navn: string;
  autorisasjon: string;
  legekontor: string;
  orgnummer?: string;
};

export const CurrentUserContext = createContext<CurrentUser | null>(null);

export function useCurrentUser() {
  return use(CurrentUserContext);
}
