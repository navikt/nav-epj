CREATE TABLE konsultasjon
(
    id                  UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    pasient_id          UUID        NOT NULL REFERENCES pasient (id),
    helsepersonell_id   UUID        NOT NULL REFERENCES helsepersonell (id),
    startet_tidspunkt   TIMESTAMPTZ NOT NULL,
    avsluttet_tidspunkt TIMESTAMPTZ,
    type                TEXT        NOT NULL, -- fysisk, video, telefon
    status              TEXT        NOT NULL, -- planlagt, pågående, fullført, avlyst
    problemstilling     TEXT,
    journalnotat        TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);