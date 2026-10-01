import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { KontrollpanelPage } from "./KontrollpanelPage";
import { seedApps, sykInn } from "./appFixtures";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { CurrentUserContext } from "./currentUser";

function renderPage(options?: { strict: boolean }) {
  return render(
    <CurrentUserContext.Provider
      value={{
        navn: "Kari Nordmann",
        hpr: "9144889",
        autorisasjon: "Lege",
        legekontor: "Storgata legekontor",
      }}
    >
      <KontrollpanelPage />
    </CurrentUserContext.Provider>,
    { reactStrictMode: options?.strict },
  );
}

describe("KontrollpanelPage", () => {
  beforeEach(() => seedApps([sykInn]));

  it("starts on the home page with the heading, the note and two categories", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s9.title"] }),
    ).toBeInTheDocument();
    expect(screen.getByText(copy["s9.note"])).toBeInTheDocument();
    expect(screen.getAllByRole("button").map((b) => b.textContent)).toEqual([
      `${copy["s9.cat.apps"]}${copy["s9.cat.apps.sub"]}`,
      `${copy["s9.cat.org"]}${copy["s9.cat.org.sub"]}`,
    ]);
  });

  it("does not steal focus on first mount, even under StrictMode", () => {
    renderPage({ strict: true });
    expect(document.body).toHaveFocus();
  });

  it("opens each category under its own heading and moves focus to it", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(copy["s9.cat.apps"]) }),
    );
    const heading = screen.getByRole("heading", {
      level: 1,
      name: copy["s9.cat.apps"],
    });
    expect(heading).toHaveFocus();
    expect(
      screen.getByRole("table", { name: copy["s9.cat.apps"] }),
    ).toBeInTheDocument();
  });

  it("shows the office and user page", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(copy["s9.cat.org"]) }),
    );
    expect(
      screen.getByRole("heading", { level: 1, name: copy["s9.cat.org"] }),
    ).toHaveFocus();
    expect(screen.getByText("Kari Nordmann")).toBeInTheDocument();
  });

  it("goes back to the home page and focuses its heading", async () => {
    renderPage();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(copy["s9.cat.org"]) }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: copy["s9.back"] }),
    );
    const heading = screen.getByRole("heading", {
      level: 1,
      name: copy["s9.title"],
    });
    expect(heading).toHaveFocus();
    expect(screen.getAllByRole("button")).toHaveLength(2);
  });

  it("has no serious accessibility violations on any page", async () => {
    const { container } = renderPage();
    await expectNoSeriousViolations(container);
    for (const name of [
      copy["s9.cat.apps"],
      copy["s9.cat.org"],
    ]) {
      await userEvent.click(
        screen.getByRole("button", { name: new RegExp(name) }),
      );
      await expectNoSeriousViolations(container);
      await userEvent.click(
        screen.getByRole("button", { name: copy["s9.back"] }),
      );
    }
  });
});
