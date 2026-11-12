import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConsultationDocuments } from "./ConsultationDocuments";
import { useJournalStore } from "./journalStore";
import { expectNoSeriousViolations } from "./axeHelper";
import type { Consultation } from "../utils/mapping/ehr";

const base = { consultationId: "k1", patientId: "p1", attachmentTitle: null };
const ready = {
  ...base,
  id: "d1",
  clinicalNote: null,
  source: "FHIR" as const,
  attachmentTitle: "sykmelding.pdf",
  hasAttachment: true,
  attachmentExpired: false,
};
const expired = { ...ready, id: "d2", attachmentTitle: "gammel.pdf", attachmentExpired: true };
const textOnly = {
  ...base,
  id: "d3",
  clinicalNote: "Svar fra app",
  source: "FHIR" as const,
  hasAttachment: false,
  attachmentExpired: false,
};
const own = { ...textOnly, id: "n1", clinicalNote: "Eget notat", source: "EHR" as const };

function consultation(clinicalNotes: Consultation["clinicalNotes"]): Consultation {
  return {
    id: "k1",
    patientId: "p1",
    hpr: [],
    clinicalNotes,
    diagnoses: [],
    startedAt: "2026-09-30T09:00:00",
    endedAt: null,
    status: "IN_PROGRESS",
    reasonForVisit: null,
  };
}

function Harness() {
  const consultations = useJournalStore((s) => s.consultations);
  return consultations[0] ? <ConsultationDocuments consultation={consultations[0]} /> : null;
}

function setup(notes: Consultation["clinicalNotes"], deleteResponse: () => Promise<Response>) {
  useJournalStore.setState({ patientId: "p1", consultations: [consultation(notes)] });
  const fetchMock = vi.fn<typeof fetch>(async () => deleteResponse());
  vi.stubGlobal("fetch", fetchMock);
  render(<Harness />);
  return fetchMock;
}

const okResponse = async () => ({ ok: true, status: 204 }) as Response;

afterEach(() => {
  useJournalStore.getState().clear();
  vi.unstubAllGlobals();
});

describe("ConsultationDocuments", () => {
  it("lists a PDF link, an expired notice without link and text-only notes, never the own note", async () => {
    setup([ready, expired, textOnly, own], okResponse);
    const section = screen.getByRole("region", { name: "Dokumenter fra apper" });
    expect(
      within(section).getByRole("link", { name: "Åpne sykmelding.pdf (PDF, ny fane)" }),
    ).toHaveAttribute("href", "/api/clinical-notes/d1/attachment");
    expect(within(section).queryByRole("link", { name: /gammel\.pdf/ })).toBeNull();
    expect(within(section).getByText("gammel.pdf")).toBeInTheDocument();
    expect(within(section).getByText("(PDF slettet etter 14 dager)")).toBeInTheDocument();
    expect(within(section).getByText("Svar fra app")).toBeInTheDocument();
    expect(within(section).queryByText("Eget notat")).toBeNull();
    expect(within(section).getAllByRole("button", { name: /^Slett / })).toHaveLength(3);
    await expectNoSeriousViolations(section);
  });

  it("renders nothing without app documents", () => {
    setup([own], okResponse);
    expect(screen.queryByRole("region")).toBeNull();
  });

  it("closes the confirmation without deleting on cancel", async () => {
    const user = userEvent.setup();
    const fetchMock = setup([ready], okResponse);
    await user.click(screen.getByRole("button", { name: "Slett sykmelding.pdf" }));
    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent("QuestionnaireResponse");
    expect(dialog).toHaveTextContent("kan ikke angres");
    await user.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(screen.queryByRole("alertdialog")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: /sykmelding\.pdf/ })).toBeInTheDocument();
  });

  it("deletes once while pending, keeping the sibling and the draft", async () => {
    const user = userEvent.setup();
    let resolve!: (response: Response) => void;
    const fetchMock = setup([ready, textOnly], () => new Promise<Response>((r) => (resolve = r)));
    useJournalStore.setState({ draft: { ...useJournalStore.getState().draft, clinicalNote: "utkast" } });
    await user.click(screen.getByRole("button", { name: "Slett sykmelding.pdf" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Slett" }));
    const pending = within(dialog).getByRole("button", { name: "Sletter …" });
    expect(pending).toHaveAttribute("aria-disabled", "true");
    await user.click(pending);
    await user.click(within(dialog).getByRole("button", { name: "Avbryt" }));
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/clinical-notes/d1", { method: "DELETE" });
    resolve({ ok: true, status: 204 } as Response);
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(screen.queryByRole("link", { name: /sykmelding\.pdf/ })).toBeNull();
    expect(screen.getByText("Svar fra app")).toBeInTheDocument();
    expect(useJournalStore.getState().draft.clinicalNote).toBe("utkast");
  });

  it("treats an already deleted document as deleted", async () => {
    const user = userEvent.setup();
    setup([ready, textOnly], async () => ({ ok: false, status: 404 }) as Response);
    await user.click(screen.getByRole("button", { name: "Slett sykmelding.pdf" }));
    await user.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "Slett" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
    expect(screen.queryByRole("link", { name: /sykmelding\.pdf/ })).toBeNull();
    expect(screen.getByText("Svar fra app")).toBeInTheDocument();
  });

  it("shows an error and allows a retry when deleting fails", async () => {
    const user = userEvent.setup();
    const fetchMock = setup([ready], async () => ({ ok: false, status: 500 }) as Response);
    await user.click(screen.getByRole("button", { name: "Slett sykmelding.pdf" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Slett" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Kunne ikke slette dokumentet");
    expect(within(dialog).getByRole("button", { name: "Slett" })).not.toHaveAttribute("aria-disabled");
    expect(screen.getByRole("link", { name: /sykmelding\.pdf/ })).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Slett" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });
});
