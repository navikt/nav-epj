-- Matematisk Ape (seeded in V2) predates the demographics columns added in V3
-- and is deliberately left with unknown Født/Alder/Kjønn to exercise the
-- "legacy patient" code path (see PasientRepositoryTest). This migration adds
-- a second seeded patient with complete demographics, so local/dev testing
-- also has a normal, fully-populated patient to look at.
INSERT INTO pasient (id, legekontor_id, fornavn, etternavn, personident, personident_type, birth_date, gender,
                      created_at, updated_at)
VALUES ('279d2ae3-5be5-4f07-a9df-c9b660201e50', 'aed5c75c-3b12-4652-83d7-223bdd69062d', 'FRISK', 'REV',
        '14038512400', 'FNR', '1985-03-14', 'FEMALE',
        '2026-10-01 12:00:00.000000 +00:00', '2026-10-01 12:00:00.000000 +00:00');

INSERT INTO pasient_helsepersonell (pasient_id, hpr)
VALUES ('279d2ae3-5be5-4f07-a9df-c9b660201e50', '565501872');
