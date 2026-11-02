CREATE TABLE patient
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    given_name       TEXT        NOT NULL,
    family_name      TEXT        NOT NULL,
    personident      TEXT        NOT NULL UNIQUE,
    personident_type TEXT,
    birth_date       DATE,
    gender           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT patient_personident_type_check
        CHECK (personident_type IN ('FNR', 'DNR')),
    CONSTRAINT patient_gender_check
        CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'UNKNOWN'))
);
