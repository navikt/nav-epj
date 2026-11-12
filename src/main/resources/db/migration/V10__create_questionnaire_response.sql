CREATE TABLE questionnaire_response
(
    id              UUID PRIMARY KEY,
    patient_id      UUID        NOT NULL REFERENCES patient (id),
    consultation_id UUID        NOT NULL REFERENCES consultation (id),
    resource        JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX questionnaire_response_patient_id_idx ON questionnaire_response (patient_id);
CREATE INDEX questionnaire_response_consultation_id_idx ON questionnaire_response (consultation_id);
