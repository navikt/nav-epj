CREATE TABLE consultation
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    patient_id       UUID        NOT NULL REFERENCES patient (id),
    clinic_id        UUID        NOT NULL REFERENCES clinic (id),
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    status           TEXT        NOT NULL, -- PLANNED, IN_PROGRESS, FINISHED, CANCELLED
    reason_for_visit TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consultation_practitioner
(
    consultation_id UUID NOT NULL REFERENCES consultation (id) ON DELETE CASCADE,
    hpr             TEXT NOT NULL
);

CREATE TABLE consultation_diagnosis_code
(
    consultation_id  UUID NOT NULL REFERENCES consultation (id),
    diagnosis_system TEXT NOT NULL,
    diagnosis_code   TEXT NOT NULL,
    PRIMARY KEY (consultation_id, diagnosis_system, diagnosis_code)
);
