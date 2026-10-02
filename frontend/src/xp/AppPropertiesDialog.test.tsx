import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AppPropertiesDialog } from "./AppPropertiesDialog";
import { sykInn, validator } from "./appFixtures";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import type { App } from "../utils/mapping/epj";

const jwtApp: App = {
  ...validator,
  launchMode: "ask",
  launchUri: "https://v.example/launch",
  tokenEndpointAuthMethod: "private_key_jwt",
  jwksUri: "https://v.example/jwks.json",
  redirectUris: ["https://v.example", "https://v.example/callback"],
  scopes: [
    "openid",
    "fhirUser",
    "launch",
    "launch/patient",
    "offline_access",
    "patient/Patient.rs",
    "patient/Observation.write",
  ],
};

function setup(app: App = sykInn) {
  const onClose = vi.fn();
  const view = render(<AppPropertiesDialog app={app} onClose={onClose} />);
  return { ...view, onClose };
}

describe("AppPropertiesDialog", () => {
  it("is a dialog titled with the app name", () => {
    setup();
    expect(
      screen.getByRole("dialog", { name: copy["s10.title"]("Sykmelding") }),
    ).toBeInTheDocument();
  });

  it("offers the three tabs with Generelt selected first", () => {
    setup();
    const tabs = screen.getAllByRole("tab");
    expect(tabs.map((t) => t.textContent)).toEqual([
      copy["s10.tab.gen"],
      copy["s10.tab.oauth"],
      copy["s10.tab.scope"],
    ]);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
  });

  it("shows the general properties from the registered app", () => {
    setup();
    const panel = screen.getByRole("tabpanel");
    expect(within(panel).getByText("Sykmelding")).toBeInTheDocument();
    expect(within(panel).getByText(sykInn.beskrivelse ?? "")).toBeInTheDocument();
    expect(within(panel).getByText(copy["s10.launchUrl"])).toBeInTheDocument();
    expect(within(panel).getByText(sykInn.launchUri ?? "")).toBeInTheDocument();
    expect(within(panel).getByText(copy["pane.apps.mode.iframe"])).toBeInTheDocument();
    expect(within(panel).getByText("syk-inn")).toBeInTheDocument();
    expect(within(panel).getByText(copy["s10.source.config"])).toBeInTheDocument();
  });

  it("shows a client_secret client with a secret that is never revealed", async () => {
    setup();
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.oauth"] }));
    const panel = screen.getByRole("tabpanel");
    expect(within(panel).getByText("client_secret_basic")).toBeInTheDocument();
    expect(within(panel).getByText(copy["s10.secret.basic"])).toBeInTheDocument();
    expect(within(panel).getByText(copy["s10.jwks.none"])).toBeInTheDocument();
    expect(within(panel).getByText("https://syk.example/callback")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/client_?secret["']?\s*[:=]/i);
  });

  it("shows a private_key_jwt client with its jwks_uri and every redirect URI", async () => {
    setup(jwtApp);
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.oauth"] }));
    const panel = screen.getByRole("tabpanel");
    expect(within(panel).getByText("private_key_jwt")).toBeInTheDocument();
    expect(within(panel).getByText(copy["s10.secret.jwt"])).toBeInTheDocument();
    expect(within(panel).getByText("https://v.example/jwks.json")).toBeInTheDocument();
    expect(within(panel).getAllByRole("listitem").map((i) => i.textContent)).toEqual(
      jwtApp.redirectUris,
    );
  });

  it("groups scopes with a Norwegian explanation for each known scope", async () => {
    setup(jwtApp);
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.scope"] }));
    const panel = screen.getByRole("tabpanel");
    expect(
      within(panel)
        .getAllByRole("heading", { level: 3 })
        .map((h) => h.textContent),
    ).toEqual([
      copy["s10.group.identity"],
      copy["s10.group.launch"],
      copy["s10.group.patient"],
      copy["s10.group.offline"],
    ]);
    expect(within(panel).getByText("patient/Patient.rs")).toBeInTheDocument();
    expect(
      within(panel).getByText(copy["scope.patient/Patient.rs"]),
    ).toBeInTheDocument();
    expect(within(panel).getByText(copy["scope.launch/patient"])).toBeInTheDocument();
    expect(within(panel).getByText(copy["scope.offline_access"])).toBeInTheDocument();
  });

  it("lists a scope without a known explanation by name only", async () => {
    setup(jwtApp);
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.scope"] }));
    const term = screen.getByText("patient/Observation.write");
    expect(term.nextElementSibling).toHaveTextContent(copy["s4.empty.value"]);
  });

  it("moves between tabs with the arrow keys", async () => {
    setup();
    screen.getByRole("tab", { name: copy["s10.tab.gen"] }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: copy["s10.tab.oauth"] })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("closes with OK, which has focus on open, and with Escape", async () => {
    const { onClose } = setup();
    const ok = screen.getByRole("button", { name: copy["common.ok"] });
    expect(ok).toHaveFocus();
    await userEvent.click(ok);
    expect(onClose).toHaveBeenCalledOnce();
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("has no serious accessibility violations on any tab", async () => {
    const { baseElement } = setup(jwtApp);
    await expectNoSeriousViolations(baseElement);
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.oauth"] }));
    await expectNoSeriousViolations(baseElement);
    await userEvent.click(screen.getByRole("tab", { name: copy["s10.tab.scope"] }));
    await expectNoSeriousViolations(baseElement);
  });
});
