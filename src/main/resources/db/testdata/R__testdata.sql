INSERT INTO legekontor (navn, tlf, orgnummer)
VALUES ('Fjordvik Legekontor', '22000001', '311111116'),
       ('Lyngdal Legesenter', '22000002', '311111124')
ON CONFLICT (orgnummer) DO UPDATE
    SET navn       = EXCLUDED.navn,
        tlf        = EXCLUDED.tlf,
        updated_at = now();

UPDATE pasient
SET personident = '14838510036'
WHERE id = '279d2ae3-5be5-4f07-a9df-c9b660201e50';

INSERT INTO pasient (fornavn, etternavn, personident, personident_type, birth_date, gender)
SELECT v.fornavn,
       v.etternavn,
       v.personident,
       v.personident_type,
       v.birth_date::date,
       v.gender
FROM (VALUES ('MATEMATISK', 'APE', '21914897936', NULL, NULL, NULL),
             ('FRISK', 'REV', '14838510036', 'FNR', '1985-03-14', 'FEMALE'),
             ('TRYGG', 'FJORD', '02867910193', 'FNR', '1979-06-02', 'MALE'),
             ('GLAD', 'FJORD', '30898110431', 'FNR', '1981-09-30', 'FEMALE'),
             ('MILD', 'VIND', '03920150631', 'FNR', '2001-12-03', 'FEMALE'),
             ('LITEN', 'FJORD', '17841250397', 'FNR', '2012-04-17', 'MALE'),
             ('STILLE', 'ELV', '65819210459', 'DNR', '1992-01-25', 'FEMALE'),
             ('RASK', 'LYNG', '49886810767', 'DNR', '1968-08-09', 'MALE'))
         AS v (fornavn, etternavn, personident, personident_type, birth_date, gender)
ON CONFLICT (personident) DO UPDATE
    SET fornavn          = EXCLUDED.fornavn,
        etternavn        = EXCLUDED.etternavn,
        personident_type = EXCLUDED.personident_type,
        birth_date       = EXCLUDED.birth_date,
        gender           = EXCLUDED.gender,
        updated_at       = now();

DELETE FROM fastlegeliste_pasient;
DELETE FROM fastlegeliste;

INSERT INTO fastlegeliste (hpr, legekontor_id)
SELECT v.hpr, (SELECT id FROM legekontor WHERE orgnummer = v.orgnummer)
FROM (VALUES ('565501872', '311111116'),
             ('900000001', '311111124')) AS v (hpr, orgnummer);

INSERT INTO fastlegeliste_pasient (fastlegeliste_id, pasient_id)
SELECT (SELECT fl.id
        FROM fastlegeliste fl
                 JOIN legekontor lk ON lk.id = fl.legekontor_id
        WHERE fl.hpr = v.hpr
          AND lk.orgnummer = v.orgnummer),
       (SELECT id FROM pasient WHERE personident = v.personident)
FROM (VALUES ('565501872', '311111116', '21914897936'),
             ('565501872', '311111116', '14838510036'),
             ('565501872', '311111116', '02867910193'),
             ('565501872', '311111116', '30898110431'),
             ('565501872', '311111116', '03920150631'),
             ('900000001', '311111124', '17841250397'),
             ('900000001', '311111124', '65819210459'),
             ('900000001', '311111124', '49886810767')) AS v (hpr, orgnummer, personident);
