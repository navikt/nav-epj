CREATE TABLE clinical_note
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID NOT NULL REFERENCES consultation (id),
    patient_id      UUID NOT NULL REFERENCES patient (id),
    clinical_note   TEXT
);
