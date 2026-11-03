import {
  differenceInMinutes,
  differenceInYears,
  format,
  parseISO,
  startOfMinute,
} from "date-fns";
import type { CreatePatientRequest, Patient } from "../utils/mapping/ehr";
import { copy } from "./copy";

type DerivedPersonident = Pick<
  CreatePatientRequest,
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

export function birthDateOf(patient: Patient) {
  return patient.birthDate ?? derivePersonident(patient.personident)?.birthDate ?? null;
}

export function genderOf(patient: Patient) {
  return patient.gender ?? derivePersonident(patient.personident)?.gender ?? null;
}

export function genderLabel(gender: Patient["gender"]) {
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

export function formatTime(date: Date, withSeconds = false) {
  return format(date, withSeconds ? "HH:mm:ss" : "HH:mm");
}

export function formatDateTime(iso: string) {
  return format(parseISO(iso), "dd.MM.yyyy HH:mm");
}

export function formatUtcDateTimeInOslo(iso: string) {
  const parts = new Intl.DateTimeFormat("nb-NO", {
    timeZone: "Europe/Oslo",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(parseISO(iso));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value ?? "";
  return `${part("day")}.${part("month")}.${part("year")} ${part("hour")}:${part("minute")}`;
}

export function ageOn(birthDate: string, now: Date) {
  return differenceInYears(now, parseISO(birthDate));
}

export function minutesSince(startIso: string, now: Date) {
  return Math.max(
    0,
    differenceInMinutes(startOfMinute(now), startOfMinute(parseISO(startIso))),
  );
}

export function fullName(patient: Pick<Patient, "givenName" | "familyName">) {
  return `${patient.givenName} ${patient.familyName}`;
}
