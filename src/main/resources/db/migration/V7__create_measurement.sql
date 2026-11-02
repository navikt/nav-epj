CREATE TABLE measurement
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    patient_id      UUID           NOT NULL REFERENCES patient (id),
    consultation_id UUID           NOT NULL REFERENCES consultation (id),
    hpr             TEXT,
    loinc_code      TEXT           NOT NULL,
    loinc_display   TEXT           NOT NULL,
    value           NUMERIC(12, 4) NOT NULL,
    unit_code       TEXT           NOT NULL,
    unit_display    TEXT           NOT NULL,
    effective_at    TIMESTAMPTZ    NOT NULL,
    status          TEXT           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT measurement_status_check
        CHECK (status IN
               ('REGISTERED', 'PRELIMINARY', 'FINAL', 'AMENDED', 'CORRECTED', 'CANCELLED',
                'ENTERED_IN_ERROR', 'UNKNOWN'))
);

CREATE INDEX measurement_patient_id_idx ON measurement (patient_id);
CREATE INDEX measurement_consultation_id_idx ON measurement (consultation_id);
