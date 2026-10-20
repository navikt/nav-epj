import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { UserGate } from "./UserGate";
import { expectNoSeriousViolations } from "./axeHelper";
import { copy } from "./copy";
import type { HelsepersonellState } from "./useHelsepersonell";

const ready: HelsepersonellState = {
  status: "ready",
  helsepersonell: {
    hpr: "1",
    legekontorId: null,
    navn: "Kari",
    autorisasjon: "Lege",
  },
  sessionOrg: {
    org: { id: "k", navn: "Kontor" },
    needsChoice: false,
    canChoose: true,
    choosableOrgs: [],
  },
};

const unresolved: HelsepersonellState = {
  ...ready,
  sessionOrg: {
    org: null,
    needsChoice: true,
    canChoose: true,
    choosableOrgs: [
      { org: { id: "a", navn: "Fjordvik Legekontor", orgnummer: "311111116" }, hasOwnList: false },
      { org: { id: "b", navn: "Lyngdal Legesenter", orgnummer: "311111124" }, hasOwnList: true },
    ],
  },
};

describe("UserGate", () => {
  it("announces loading and hides children", async () => {
    const { container } = render(
      <UserGate state={{ status: "loading" }} onRetry={() => {}}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByRole("status")).toHaveTextContent(copy["s1.loading"]);
    expect(screen.queryByText("Innhold")).not.toBeInTheDocument();
    await expectNoSeriousViolations(container);
  });

  it("shows an alert with retry on error", async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    const { container } = render(
      <UserGate state={{ status: "error" }} onRetry={onRetry}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      copy["s1.error.title"],
    );
    expect(screen.getByText(copy["s1.error.body"])).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: copy["s1.error.retry"] }),
    );
    expect(onRetry).toHaveBeenCalledOnce();
    await expectNoSeriousViolations(container);
  });

  it("renders children when ready", () => {
    render(
      <UserGate state={ready} onRetry={() => {}}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.getByText("Innhold")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("asks for an org instead of the children when none is resolved", async () => {
    const user = userEvent.setup();
    const onChooseOrg = vi.fn().mockResolvedValue(undefined);
    const { container } = render(
      <UserGate state={unresolved} onRetry={() => {}} onChooseOrg={onChooseOrg}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.queryByText("Innhold")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: copy["org.pick.title"] }),
    ).toBeInTheDocument();
    expect(screen.getAllByText(copy["org.pick.ownList"])).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: copy["org.pick.cancel"] }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Lyngdal Legesenter/ }));
    expect(onChooseOrg).toHaveBeenCalledWith("311111124");
    await expectNoSeriousViolations(container);
  });

  it("shows an error when choosing fails", async () => {
    const user = userEvent.setup();
    render(
      <UserGate
        state={unresolved}
        onRetry={() => {}}
        onChooseOrg={() => Promise.reject(new Error("409"))}
      >
        <p>Innhold</p>
      </UserGate>,
    );
    await user.click(screen.getByRole("button", { name: /Fjordvik/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      copy["org.pick.error"],
    );
  });

  it("lets the user re-select the current org", async () => {
    const user = userEvent.setup();
    const onChooseOrg = vi.fn().mockResolvedValue(undefined);
    const org = { id: "k", navn: "Kontor", orgnummer: "311111116" };
    render(
      <UserGate
        state={{
          ...ready,
          sessionOrg: { org, needsChoice: false, canChoose: true, choosableOrgs: [{ org, hasOwnList: false }] },
        }}
        onRetry={() => {}}
        onChooseOrg={onChooseOrg}
        changingOrg
      >
        <p>Innhold</p>
      </UserGate>,
    );
    const button = screen.getByRole("button", { name: /Kontor/ });
    expect(button).toBeEnabled();
    await user.click(button);
    expect(onChooseOrg).toHaveBeenCalledWith("311111116");
  });

  it("shows the picker with cancel when the user is changing org", async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(
      <UserGate state={ready} onRetry={() => {}} changingOrg onCancelChangeOrg={onCancel}>
        <p>Innhold</p>
      </UserGate>,
    );
    expect(screen.queryByText("Innhold")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: copy["org.pick.cancel"] }));
    expect(onCancel).toHaveBeenCalledOnce();
  });
});
