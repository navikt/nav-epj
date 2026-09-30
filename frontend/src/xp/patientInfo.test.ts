import { describe, expect, it } from "vitest";
import {
  ageOn,
  birthDateOf,
  derivePersonident,
  formatDate,
  formatDateTime,
  fullName,
  genderLabel,
  genderOf,
  maskPersonident,
} from "./patientInfo";
import type { Pasient } from "../utils/mapping/epj";

const pasient: Pasient = {
  id: "1",
  fornavn: "Matematisk",
  etternavn: "Ape",
  personident: "01019012345",
  personidentType: "FNR",
  birthDate: null,
  gender: null,
};

describe("patientInfo", () => {
  it("masks all but the last five digits", () => {
    expect(maskPersonident("01019012345")).toBe("******12345");
  });

  it("derives a fødselsnummer", () => {
    expect(derivePersonident("01019012345")).toEqual({
      personidentType: "FNR",
      birthDate: "1990-01-01",
      gender: "MALE",
    });
  });

  it("derives a D-nummer", () => {
    expect(derivePersonident("41019012345")).toEqual({
      personidentType: "DNR",
      birthDate: "1990-01-01",
      gender: "MALE",
    });
  });

  it("resolves centuries from the individual number", () => {
    expect(derivePersonident("01010050000")?.birthDate).toBe("2000-01-01");
    expect(derivePersonident("01015450000")?.birthDate).toBe("1854-01-01");
    expect(derivePersonident("01019490000")?.birthDate).toBe("1994-01-01");
  });

  it("derives gender from the third individual digit", () => {
    expect(derivePersonident("01010050000")?.gender).toBe("FEMALE");
  });

  it("rejects malformed and impossible identities", () => {
    expect(derivePersonident("123")).toBeNull();
    expect(derivePersonident("abcdefghijk")).toBeNull();
    expect(derivePersonident("31029012345")).toBeNull();
    expect(derivePersonident("01014050000")).toBeNull();
  });

  it("prefers stored birth date and gender over derived values", () => {
    expect(birthDateOf(pasient)).toBe("1990-01-01");
    expect(genderOf(pasient)).toBe("MALE");
    expect(
      birthDateOf({ ...pasient, birthDate: "1985-05-05", gender: "FEMALE" }),
    ).toBe("1985-05-05");
    expect(
      genderOf({ ...pasient, birthDate: "1985-05-05", gender: "FEMALE" }),
    ).toBe("FEMALE");
  });

  it("returns null when nothing can be derived", () => {
    const unknown = { ...pasient, personident: "abc" };
    expect(birthDateOf(unknown)).toBeNull();
    expect(genderOf(unknown)).toBeNull();
  });

  it("labels genders", () => {
    expect(genderLabel("MALE")).toBe("Mann");
    expect(genderLabel("FEMALE")).toBe("Kvinne");
    expect(genderLabel("OTHER")).toBe("Annet");
    expect(genderLabel("UNKNOWN")).toBe("Ukjent");
    expect(genderLabel(null)).toBe("Ukjent");
  });

  it("formats dates and computes age", () => {
    expect(formatDate("1990-01-01")).toBe("01.01.1990");
    expect(formatDateTime("2026-09-30T09:14:05.123")).toBe("30.09.2026 09:14");
    expect(ageOn("1990-10-01", new Date(2026, 8, 30))).toBe(35);
    expect(ageOn("1990-09-30", new Date(2026, 8, 30))).toBe(36);
  });

  it("joins the name", () => {
    expect(fullName(pasient)).toBe("Matematisk Ape");
  });
});
