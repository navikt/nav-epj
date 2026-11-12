ALTER TABLE clinical_note
    ADD COLUMN source                            TEXT        NOT NULL DEFAULT 'EHR',
    ADD COLUMN created_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN type_text                         TEXT,
    ADD COLUMN period_start                      DATE,
    ADD COLUMN period_end                        DATE,
    ADD COLUMN related_questionnaire_response_id UUID,
    ADD COLUMN author_hprs                       TEXT[]      NOT NULL DEFAULT '{}',
    ADD CONSTRAINT clinical_note_source_check CHECK (source IN ('EHR', 'FHIR'));

CREATE INDEX clinical_note_consultation_id_idx ON clinical_note (consultation_id);

CREATE TABLE clinical_note_attachment
(
    clinical_note_id UUID PRIMARY KEY REFERENCES clinical_note (id) ON DELETE CASCADE,
    title            TEXT,
    language         TEXT,
    content_type     TEXT        NOT NULL,
    size_bytes       INTEGER     NOT NULL,
    data             BYTEA,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX clinical_note_attachment_retention_idx
    ON clinical_note_attachment (created_at) WHERE data IS NOT NULL;
