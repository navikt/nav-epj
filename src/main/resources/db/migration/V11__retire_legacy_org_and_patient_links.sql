INSERT INTO fastlegeliste (hpr, legekontor_id)
SELECT DISTINCT ph.hpr, p.legekontor_id
FROM pasient_helsepersonell ph
         JOIN pasient p ON p.id = ph.pasient_id
WHERE ph.hpr IS NOT NULL
  AND p.legekontor_id IS NOT NULL
ON CONFLICT (hpr, legekontor_id) DO NOTHING;

INSERT INTO fastlegeliste_pasient (fastlegeliste_id, pasient_id)
SELECT DISTINCT ON (ph.pasient_id) fl.id, ph.pasient_id
FROM pasient_helsepersonell ph
         JOIN pasient p ON p.id = ph.pasient_id
         JOIN fastlegeliste fl ON fl.hpr = ph.hpr AND fl.legekontor_id = p.legekontor_id
ORDER BY ph.pasient_id, ph.hpr
ON CONFLICT (pasient_id) DO NOTHING;

DROP TABLE pasient_helsepersonell;

ALTER TABLE pasient
    DROP COLUMN legekontor_id;

ALTER TABLE helsepersonell
    DROP COLUMN legekontor_id;
