ALTER TABLE helsepersonell
ADD COLUMN her_id TEXT;

CREATE TABLE konsultasjon_helsepersonell
(
    konsultasjon_id   UUID NOT NULL REFERENCES konsultasjon(id) ON DELETE CASCADE,
    hpr               TEXT NOT NULL
);

ALTER TABLE konsultasjon
DROP COLUMN helsepersonell_id;