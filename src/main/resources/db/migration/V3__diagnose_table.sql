CREATE TABLE diagnose
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    konsultasjon_id UUID NOT NULL REFERENCES konsultasjon (id),
    diagnosekode    TEXT NOT NULL,
    diagnosesystem  TEXT NOT NULL
);