import { describe, expect, it } from "vitest";
import { copy } from "./copy";
import { explanationOf, groupScopes, scopeGroupOf } from "./scopes";

describe("scopes", () => {
  it("places every scope in its group", () => {
    expect(scopeGroupOf("openid")).toBe("identity");
    expect(scopeGroupOf("fhirUser")).toBe("identity");
    expect(scopeGroupOf("profile")).toBe("identity");
    expect(scopeGroupOf("launch")).toBe("launch");
    expect(scopeGroupOf("launch/patient")).toBe("launch");
    expect(scopeGroupOf("patient/Patient.rs")).toBe("patient");
    expect(scopeGroupOf("offline_access")).toBe("offline");
  });

  it("groups in a fixed order, keeps scope order and drops empty groups", () => {
    expect(
      groupScopes([
        "patient/Encounter.rs",
        "offline_access",
        "openid",
        "patient/Patient.rs",
      ]),
    ).toEqual([
      { id: "identity", label: copy["s10.group.identity"], scopes: ["openid"] },
      {
        id: "patient",
        label: copy["s10.group.patient"],
        scopes: ["patient/Encounter.rs", "patient/Patient.rs"],
      },
      {
        id: "offline",
        label: copy["s10.group.offline"],
        scopes: ["offline_access"],
      },
    ]);
  });

  it("returns no groups for no scopes", () => {
    expect(groupScopes([])).toEqual([]);
  });

  it("explains known scopes in Norwegian", () => {
    expect(explanationOf("patient/Patient.rs")).toBe(
      "lese og søke i pasientopplysninger",
    );
    expect(explanationOf("launch/patient")).toBe(
      "be om pasientkontekst ved oppstart",
    );
  });

  it("has no explanation for unknown scopes or inherited object keys", () => {
    expect(explanationOf("patient/Observation.write")).toBeUndefined();
    expect(explanationOf("constructor")).toBeUndefined();
  });
});
