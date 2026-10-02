import { describe, expect, it } from "vitest";
import { filterPatients } from "./patientFilter";
import type { Pasient } from "../utils/mapping/epj";

const make = (id: string, fornavn: string, etternavn: string, personident: string): Pasient => ({
  id,
  fornavn,
  etternavn,
  personident,
  personidentType: "FNR",
  birthDate: null,
  gender: null,
});

const patients = [
  make("1", "Matematisk", "Ape", "01019012345"),
  make("2", "Ola", "Nordmann", "02029054321"),
];

describe("filterPatients", () => {
  it("returns everything for an empty or blank query", () => {
    expect(filterPatients(patients, "")).toHaveLength(2);
    expect(filterPatients(patients, "   ")).toHaveLength(2);
  });

  it("matches full name case-insensitively", () => {
    expect(filterPatients(patients, "matematisk ape").map((p) => p.id)).toEqual(["1"]);
    expect(filterPatients(patients, "NORD").map((p) => p.id)).toEqual(["2"]);
  });

  it("matches on the last digits of the fødselsnummer", () => {
    expect(filterPatients(patients, "54321").map((p) => p.id)).toEqual(["2"]);
    expect(filterPatients(patients, "0101 9012345").map((p) => p.id)).toEqual(["1"]);
  });

  it("returns nothing when nothing matches", () => {
    expect(filterPatients(patients, "zzz")).toEqual([]);
  });
});
