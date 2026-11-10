-- A clinical note created through FHIR can carry a document, such as a PDF a SMART app writes back.
-- The note text stays the journal entry, and the document is stored next to it.
ALTER TABLE clinical_note
    ADD COLUMN attachment_title TEXT,
    ADD COLUMN attachment_data  BYTEA;
