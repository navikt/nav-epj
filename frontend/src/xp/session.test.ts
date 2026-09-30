import { describe, expect, it } from "vitest";
import { createSession } from "./session";

describe("createSession", () => {
  it("marks earlier captures stale once a new session starts", () => {
    const session = createSession();
    session.next();
    const first = session.capture();
    expect(first()).toBe(true);
    session.next();
    expect(first()).toBe(false);
    expect(session.capture()()).toBe(true);
  });
});
