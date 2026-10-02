import { afterEach, describe, expect, it, vi } from "vitest";
import { LOGOUT_PATH, logout } from "./logout";

afterEach(() => vi.unstubAllGlobals());

describe("logout", () => {
  it("navigates to the Wonderwall logout endpoint", () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });
    logout();
    expect(assign).toHaveBeenCalledWith(LOGOUT_PATH);
    expect(LOGOUT_PATH).toBe("/oauth2/logout");
  });
});
