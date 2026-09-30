import { differenceInYears, format, parseISO } from "date-fns";
import type { OpprettPasientRequest, Pasient } from "../utils/mapping/epj";
import { copy } from "./copy";

type DerivedPersonident = Pick<
  OpprettPasientRequest,
  "personidentType" | "birthDate" | "gender"
>;

const MASK = "******";

export function maskPersonident(personident: string) {
  return `${MASK}${personident.slice(-5)}`;
}

export function personidentTail(personident: string) {
  return personident.slice(-5);
}

function resolveCentury(individnummer: number, twoDigitYear: number) {
  if (individnummer < 500) return 1900;
  if (individnummer < 750 && twoDigitYear >= 54) return 1800;
  if (individnummer >= 900 && twoDigitYear >= 40) return 1900;
  if (twoDigitYear < 40) return 2000;
  return null;
}

export function derivePersonident(
  personident: string,
): DerivedPersonident | null {
  if (!/^\d{11}$/.test(personident)) return null;
  const digits = personident.split("").map(Number);
  const encodedDay = digits[0] * 10 + digits[1];
  const month = digits[2] * 10 + digits[3];
  const twoDigitYear = digits[4] * 10 + digits[5];
  const individnummer = digits[6] * 100 + digits[7] * 10 + digits[8];
  const isDnr = encodedDay > 40;
  const day = isDnr ? encodedDay - 40 : encodedDay;
  const century = resolveCentury(individnummer, twoDigitYear);
  if (century === null) return null;
  const year = century + twoDigitYear;
  const date = new Date(year, month - 1, day);
  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    return null;
  }
  return {
    personidentType: isDnr ? "DNR" : "FNR",
    birthDate: format(date, "yyyy-MM-dd"),
    gender: digits[8] % 2 === 1 ? "MALE" : "FEMALE",
  };
}

export function birthDateOf(pasient: Pasient) {
  return pasient.birthDate ?? derivePersonident(pasient.personident)?.birthDate ?? null;
}

export function genderOf(pasient: Pasient) {
  return pasient.gender ?? derivePersonident(pasient.personident)?.gender ?? null;
}

export function genderLabel(gender: Pasient["gender"]) {
  switch (gender) {
    case "MALE":
      return copy["context.gender.m"];
    case "FEMALE":
      return copy["context.gender.k"];
    case "OTHER":
      return copy["context.gender.other"];
    default:
      return copy["context.gender.unknown"];
  }
}

export function formatDate(iso: string) {
  return format(parseISO(iso), "dd.MM.yyyy");
}

export function formatDateTime(iso: string) {
  return format(parseISO(iso), "dd.MM.yyyy HH:mm");
}

export function ageOn(birthDate: string, now: Date) {
  return differenceInYears(now, parseISO(birthDate));
}

export function fullName(pasient: Pick<Pasient, "fornavn" | "etternavn">) {
  return `${pasient.fornavn} ${pasient.etternavn}`;
}
