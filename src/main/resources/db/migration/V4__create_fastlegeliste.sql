CREATE TABLE fastlegeliste
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    hpr        TEXT        NOT NULL,
    clinic_id  UUID        NOT NULL REFERENCES clinic (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fastlegeliste_hpr_clinic_unique UNIQUE (hpr, clinic_id)
);

CREATE TABLE fastlegeliste_patient
(
    fastlegeliste_id UUID NOT NULL REFERENCES fastlegeliste (id) ON DELETE CASCADE,
    patient_id       UUID NOT NULL REFERENCES patient (id),
    CONSTRAINT fastlegeliste_patient_patient_unique UNIQUE (patient_id)
);
