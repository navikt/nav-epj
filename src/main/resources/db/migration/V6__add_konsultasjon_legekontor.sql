ALTER TABLE konsultasjon
    ADD COLUMN legekontor_id UUID REFERENCES legekontor (id);

UPDATE konsultasjon k
SET legekontor_id = p.legekontor_id
FROM pasient p
WHERE k.pasient_id = p.id
  AND k.legekontor_id IS NULL;

-- pasient.legekontor_id is NOT NULL and konsultasjon.pasient_id is a mandatory
-- FK to pasient, so every konsultasjon row has a truthful, unambiguous owning
-- organization after the backfill above. Enforcing NOT NULL makes that a
-- database-level guarantee instead of a convention.
ALTER TABLE konsultasjon
    ALTER COLUMN legekontor_id SET NOT NULL;
