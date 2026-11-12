import { useState } from "react";
import { Button } from "./Button";
import { DeleteDocumentDialog } from "./DeleteDocumentDialog";
import { copy } from "./copy";
import { documentsOf, useJournalStore } from "./journalStore";
import { useShell } from "./shellContext";
import type { Consultation } from "../utils/mapping/ehr";

type Props = {
  consultation: Consultation;
};

type Document = ReturnType<typeof documentsOf>[number];

function titleOf(document: Document) {
  if (document.attachmentTitle) return document.attachmentTitle;
  return document.hasAttachment
    ? copy["s4.documents.fallbackTitle"]
    : copy["s4.documents.untitled"];
}

export function ConsultationDocuments({ consultation }: Props) {
  const { announce } = useShell();
  const [deleting, setDeleting] = useState<Document | null>(null);
  const documents = documentsOf(consultation);
  if (documents.length === 0) return null;

  async function confirmDelete(document: Document) {
    const ok = await useJournalStore.getState().deleteDocument(document.id);
    if (ok) {
      setDeleting(null);
      announce(copy["live.documentDeleted"]);
    }
    return ok;
  }

  return (
    <section aria-label={copy["s4.documents.label"]}>
      <h3>{copy["s4.documents.label"]}</h3>
      <ul>
        {documents.map((document) => {
          const title = titleOf(document);
          return (
            <li key={document.id}>
              {document.hasAttachment && !document.attachmentExpired && (
                <a
                  className="xp-link"
                  href={`/api/clinical-notes/${encodeURIComponent(document.id)}/attachment`}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={copy["s4.documents.open"](title)}
                >
                  {title}
                </a>
              )}
              {document.hasAttachment && document.attachmentExpired && (
                <>
                  <span>{title}</span>{" "}
                  <span>({copy["s4.documents.expired"]})</span>
                </>
              )}
              {!document.hasAttachment && <strong>{title}</strong>}{" "}
              <Button
                variant="small"
                aria-label={copy["s4.documents.deleteAria"](title)}
                onClick={() => setDeleting(document)}
              >
                {copy["s4.documents.delete"]}
              </Button>
              {document.clinicalNote && (
                <p className="xp-prewrap">{document.clinicalNote}</p>
              )}
            </li>
          );
        })}
      </ul>
      {deleting && (
        <DeleteDocumentDialog
          title={titleOf(deleting)}
          onConfirm={() => confirmDelete(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </section>
  );
}
