import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Measurements } from "./Measurements";
import { expectNoSeriousViolations } from "./axeHelper";

const measurement = (overrides: Record<string, unknown> = {}) => ({
  id: "m1",
  patientId: "p1",
  consultationId: "k1",
  hpr: null,
  loincCode: "8310-5",
  loincDisplay: "Body temperature",
  value: 37.2,
  unitCode: "Cel",
  unitDisplay: "degree Celsius",
  effectiveAt: "2026-09-30T09:15:00Z",
  status: "FINAL",
  ...overrides,
});

function stubFetch(...results: { ok: boolean; body?: unknown }[]) {
  const fetchMock = vi.fn();
  for (const r of results) {
    fetchMock.mockResolvedValueOnce({
      ok: r.ok,
      status: r.ok ? 200 : 500,
      json: async () => r.body,
    });
  }
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("Measurements", () => {
  it("shows loading, then measurements for the patient", async () => {
    const fetchMock = stubFetch({
      ok: true,
      body: [
        measurement(),
        measurement({
          id: "m2",
          loincCode: "8867-4",
          loincDisplay: "Heart rate",
          value: 72,
          unitCode: "/min",
          unitDisplay: "per minute",
          effectiveAt: "2026-10-01T10:00:00Z",
          status: "PRELIMINARY",
        }),
      ],
    });
    const { container } = render(<Measurements patientId="p1" />);
    expect(screen.getByRole("status")).toHaveTextContent("Laster");

    const table = await screen.findByRole("table", { name: "Målinger" });
    expect(fetchMock).toHaveBeenCalledWith("/api/patient/p1/measurements", undefined);
    const rows = within(table).getAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent("Heart rate");
    expect(rows[1]).toHaveTextContent("8867-4");
    expect(rows[1]).toHaveTextContent("72 per minute (/min)");
    expect(rows[1]).toHaveTextContent("01.10.2026 12:00");
    expect(rows[1]).toHaveTextContent("Foreløpig");
    expect(rows[2]).toHaveTextContent("Body temperature");
    expect(rows[2]).toHaveTextContent("37.2 degree Celsius (Cel)");
    expect(rows[2]).toHaveTextContent("30.09.2026 11:15");
    expect(rows[2]).toHaveTextContent("Endelig");
    await expectNoSeriousViolations(container);
  });

  it("shows an empty state", async () => {
    stubFetch({ ok: true, body: [] });
    render(<Measurements patientId="p1" />);
    expect(await screen.findByText("Ingen målinger registrert.")).toBeVisible();
  });

  it("shows an error with retry when the request fails", async () => {
    const fetchMock = stubFetch(
      { ok: false },
      { ok: true, body: [measurement()] },
    );
    render(<Measurements patientId="p1" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Feil ved lasting av målinger",
    );

    await userEvent.click(screen.getByRole("button", { name: "Prøv igjen" }));

    await waitFor(() => expect(screen.getByRole("table")).toBeVisible());
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
