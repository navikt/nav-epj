CREATE TABLE legekontor
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    navn       TEXT        NOT NULL,
    tlf        TEXT        NOT NULL,
    orgnummer  TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE helsepersonell
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    hpr           TEXT        NOT NULL UNIQUE,
    her_id        TEXT,
    navn          TEXT        NOT NULL,
    autorisasjon  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pasient
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    fastlege      UUID        NOT NULL REFERENCES helsepersonell (id),
    navn          TEXT        NOT NULL,
    fnr           TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE konsultasjon
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    pasient_id          UUID        NOT NULL REFERENCES pasient (id),
    startet_tidspunkt   TIMESTAMPTZ NOT NULL,
    avsluttet_tidspunkt TIMESTAMPTZ,
    status              TEXT        NOT NULL, -- planlagt, pågående, fullført, avlyst
    problemstilling     TEXT,
    journalnotat        TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE diagnose
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    konsultasjon_id UUID NOT NULL REFERENCES konsultasjon (id),
    beskrivelse     TEXT,
    diagnosekode    TEXT NOT NULL,
    diagnosesystem  TEXT NOT NULL
);

CREATE TABLE konsultasjon_helsepersonell
(
    konsultasjon_id   UUID NOT NULL REFERENCES konsultasjon(id) ON DELETE CASCADE,
    hpr               TEXT NOT NULL
);

INSERT INTO legekontor (id, navn)
VALUES ('a1000000-0000-0000-0000-000000000001', 'Tonsberg Legesenter', 'tulletlf', '123');
