import { createContext, use } from "react";

export type CurrentUser = {
  navn: string;
  hpr: string;
  autorisasjon: string;
  clinic: string;
  orgNumber?: string;
  phone?: string;
};

export const CurrentUserContext = createContext<CurrentUser | null>(null);

export function useCurrentUser() {
  return use(CurrentUserContext);
}
