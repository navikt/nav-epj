CREATE UNIQUE INDEX legekontor_orgnummer_unique
    ON legekontor (orgnummer);

CREATE TABLE fastlegeliste
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    hpr           TEXT        NOT NULL,
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fastlegeliste_hpr_legekontor_unique UNIQUE (hpr, legekontor_id)
);

CREATE TABLE fastlegeliste_pasient
(
    fastlegeliste_id UUID NOT NULL REFERENCES fastlegeliste (id) ON DELETE CASCADE,
    pasient_id       UUID NOT NULL REFERENCES pasient (id),
    CONSTRAINT fastlegeliste_pasient_pasient_unique UNIQUE (pasient_id)
);
