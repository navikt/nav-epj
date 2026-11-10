import { copy } from "./copy";
import { documentsOf } from "./journalStore";
import type { Consultation } from "../utils/mapping/ehr";

type Props = {
  consultation: Consultation;
};

/** Documents SMART apps wrote back to the consultation, such as a submitted form as PDF. */
export function ConsultationDocuments({ consultation }: Props) {
  const documents = documentsOf(consultation);
  if (documents.length === 0) return null;
  return (
    <section aria-label={copy["s4.documents.label"]}>
      <h3>{copy["s4.documents.label"]}</h3>
      <ul>
        {documents.map((document) => {
          const title = document.attachmentTitle ?? "dokument.pdf";
          return (
            <li key={document.id}>
              <a
                className="xp-link"
                href={`/api/clinical-notes/${document.id}/attachment`}
                target="_blank"
                rel="noopener noreferrer"
                aria-label={copy["s4.documents.open"](title)}
              >
                {title}
              </a>
              {document.clinicalNote && (
                <p className="xp-prewrap">{document.clinicalNote}</p>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
