ALTER TABLE konsultasjon
    ADD COLUMN legekontor_id UUID REFERENCES legekontor (id);

UPDATE konsultasjon k
SET legekontor_id = p.legekontor_id
FROM pasient p
WHERE k.pasient_id = p.id
  AND k.legekontor_id IS NULL;

ALTER TABLE konsultasjon
    ALTER COLUMN legekontor_id SET NOT NULL;
