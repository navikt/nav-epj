CREATE TABLE practitioner
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    hpr           TEXT        NOT NULL UNIQUE,
    name          TEXT        NOT NULL,
    qualification TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
