import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { KontrollOrgPage } from "./KontrollOrgPage";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import { CurrentUserContext, type CurrentUser } from "./currentUser";

const user: CurrentUser = {
  navn: "Kari Nordmann",
  hpr: "9144889",
  autorisasjon: "Lege",
  legekontor: "Storgata legekontor",
  orgnummer: "123456789",
  telefon: "22 33 44 55",
};

function renderPage(value: CurrentUser | null = user) {
  return render(
    <CurrentUserContext.Provider value={value}>
      <KontrollOrgPage />
    </CurrentUserContext.Provider>,
  );
}

function card(name: string) {
  return within(screen.getByRole("region", { name }));
}

describe("KontrollOrgPage", () => {
  it("shows the legekontor as read-only facts", () => {
    renderPage();
    const office = card(copy["s9.org.office"]);
    expect(office.getByText(copy["s9.org.name"]).nextElementSibling).toHaveTextContent(
      "Storgata legekontor",
    );
    expect(office.getByText(copy["s9.org.orgnr"]).nextElementSibling).toHaveTextContent(
      "123456789",
    );
    expect(office.getByText(copy["s9.org.phone"]).nextElementSibling).toHaveTextContent(
      "22 33 44 55",
    );
  });

  it("shows the logged in user with the fixed login description", () => {
    renderPage();
    const person = card(copy["s9.org.user"]);
    expect(person.getByText(copy["s9.org.name"]).nextElementSibling).toHaveTextContent(
      "Kari Nordmann",
    );
    expect(person.getByText(copy["s9.org.hpr"]).nextElementSibling).toHaveTextContent(
      "9144889",
    );
    expect(person.getByText(copy["s9.org.auth"]).nextElementSibling).toHaveTextContent(
      "Lege",
    );
    expect(person.getByText(copy["s9.org.login"]).nextElementSibling).toHaveTextContent(
      copy["s9.org.loginValue"],
    );
    expect(screen.getByText(copy["s9.org.note"])).toBeInTheDocument();
  });

  it("shows a dash for a missing org number or phone", () => {
    renderPage({ ...user, orgnummer: undefined, telefon: undefined });
    const office = card(copy["s9.org.office"]);
    expect(office.getByText(copy["s9.org.orgnr"]).nextElementSibling).toHaveTextContent(
      copy["s4.empty.value"],
    );
    expect(office.getByText(copy["s9.org.phone"]).nextElementSibling).toHaveTextContent(
      copy["s4.empty.value"],
    );
  });

  it("renders nothing without a logged in user", () => {
    const { container } = renderPage(null);
    expect(container).toBeEmptyDOMElement();
  });

  it("has no serious accessibility violations", async () => {
    const { container } = renderPage();
    await expectNoSeriousViolations(container);
  });
});
