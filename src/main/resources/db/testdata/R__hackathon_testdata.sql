INSERT INTO legekontor (navn, tlf, orgnummer)
VALUES ('Skogtun Legekontor', '22000011', '312000016'),
       ('Havnegata Legesenter', '22000012', '312000024'),
       ('Bjerkely Legepraksis', '22000013', '312000032'),
       ('Solbakken Familielegekontor', '22000014', '312000040')
ON CONFLICT (orgnummer) DO UPDATE
    SET navn       = EXCLUDED.navn,
        tlf        = EXCLUDED.tlf,
        updated_at = now();

INSERT INTO pasient (fornavn, etternavn, personident, personident_type, birth_date, gender)
SELECT v.fornavn,
       v.etternavn,
       v.personident,
       v.personident_type,
       v.birth_date::date,
       v.gender
FROM (VALUES ('BJARNE', 'BERGE', '20856210112', 'FNR', '1962-05-20', 'MALE'),
             ('INGRID', 'BERGE', '03916410017', 'FNR', '1964-11-03', 'FEMALE'),
             ('TORE', 'LUND', '28815510331', 'FNR', '1955-01-28', 'MALE'),
             ('MARIT', 'ENGEN', '55878910264', 'DNR', '1989-07-15', 'FEMALE'),
             ('SIGRUN', 'MOEN', '09894810030', 'FNR', '1948-09-09', 'FEMALE'),
             ('OLAV', 'HAUG', '70837710381', 'DNR', '1977-03-30', 'MALE'),
             ('NORA', 'ASPLUND', '12901550261', 'FNR', '2015-10-12', 'FEMALE'),
             ('KARL', 'DAHL', '22927110176', 'FNR', '1971-12-22', 'MALE'),
             ('ELSE', 'STRAND', '04845310039', 'FNR', '1953-04-04', 'FEMALE'),
             ('ANDERS', 'VIK', '18888410364', 'FNR', '1984-08-18', 'MALE'),
             ('HILDE', 'NES', '65866910027', 'DNR', '1969-06-25', 'FEMALE'),
             ('JON', 'STRAND', '14828010132', 'FNR', '1980-02-14', 'MALE'),
             ('LIV', 'ROSS', '01909410033', 'FNR', '1994-10-01', 'FEMALE'),
             ('SVEN', 'BAKKE', '47876010166', 'DNR', '1960-07-07', 'MALE'),
             ('AMINA', 'HASSAN', '45859810059', 'DNR', '1998-05-05', 'FEMALE'),
             ('PER', 'TVEIT', '12924510194', 'FNR', '1945-12-12', 'MALE'),
             ('ASTRID', 'BERGE', '17819010269', 'FNR', '1990-01-17', 'FEMALE'),
             ('GUNNAR', 'FOSS', '23895710156', 'FNR', '1957-09-23', 'MALE'),
             ('RANDI', 'SOLHEIM', '08837310041', 'FNR', '1973-03-08', 'FEMALE'),
             ('EMIL', 'KROG', '30860850180', 'FNR', '2008-06-30', 'MALE'),
             ('TOMAS', 'NOVAK', '59918610175', 'DNR', '1986-11-19', 'MALE'),
             ('KARIN', 'LIE', '27846610079', 'FNR', '1966-04-27', 'FEMALE'),
             ('YNGVE', 'ROSTED', '42885110163', 'DNR', '1951-08-02', 'MALE'),
             ('LINN', 'HOLM', '66820350082', 'DNR', '2003-02-26', 'FEMALE'),
             ('MARIANNE', 'STRAND', '05928210261', 'FNR', '1982-12-05', 'FEMALE'),
             ('HELGE', 'BRATT', '16834910156', 'FNR', '1949-03-16', 'MALE'),
             ('TUVA', 'MYRE', '14899610025', 'FNR', '1996-09-14', 'FEMALE'),
             ('ROLF', 'SAND', '69906310147', 'DNR', '1963-10-29', 'MALE'),
             ('EVA', 'LUNDE', '31857810085', 'FNR', '1978-05-31', 'FEMALE'),
             ('SAMIR', 'ALI', '49819110351', 'DNR', '1991-01-09', 'MALE'),
             ('BERIT', 'ODEGAARD', '21865810048', 'FNR', '1958-06-21', 'FEMALE'),
             ('LARS', 'KVAM', '13841050147', 'FNR', '2010-04-13', 'MALE'))
         AS v (fornavn, etternavn, personident, personident_type, birth_date, gender)
ON CONFLICT (personident) DO UPDATE
    SET fornavn          = EXCLUDED.fornavn,
        etternavn        = EXCLUDED.etternavn,
        personident_type = EXCLUDED.personident_type,
        birth_date       = EXCLUDED.birth_date,
        gender           = EXCLUDED.gender,
        updated_at       = now();

INSERT INTO fastlegeliste (hpr, legekontor_id)
SELECT v.hpr, (SELECT id FROM legekontor WHERE orgnummer = v.orgnummer)
FROM (VALUES ('900000101', '312000016'),
             ('900000102', '312000024'),
             ('900000103', '312000032'),
             ('900000104', '312000040')) AS v (hpr, orgnummer)
ON CONFLICT (hpr, legekontor_id) DO NOTHING;

INSERT INTO fastlegeliste_pasient (fastlegeliste_id, pasient_id)
SELECT (SELECT fl.id
        FROM fastlegeliste fl
                 JOIN legekontor lk ON lk.id = fl.legekontor_id
        WHERE fl.hpr = v.hpr
          AND lk.orgnummer = v.orgnummer),
       (SELECT id FROM pasient WHERE personident = v.personident)
FROM (VALUES ('900000101', '312000016', '20856210112'),
             ('900000101', '312000016', '03916410017'),
             ('900000101', '312000016', '28815510331'),
             ('900000101', '312000016', '55878910264'),
             ('900000101', '312000016', '09894810030'),
             ('900000101', '312000016', '70837710381'),
             ('900000101', '312000016', '12901550261'),
             ('900000101', '312000016', '22927110176'),
             ('900000102', '312000024', '04845310039'),
             ('900000102', '312000024', '18888410364'),
             ('900000102', '312000024', '65866910027'),
             ('900000102', '312000024', '14828010132'),
             ('900000102', '312000024', '01909410033'),
             ('900000102', '312000024', '47876010166'),
             ('900000102', '312000024', '45859810059'),
             ('900000102', '312000024', '12924510194'),
             ('900000103', '312000032', '17819010269'),
             ('900000103', '312000032', '23895710156'),
             ('900000103', '312000032', '08837310041'),
             ('900000103', '312000032', '30860850180'),
             ('900000103', '312000032', '59918610175'),
             ('900000103', '312000032', '27846610079'),
             ('900000103', '312000032', '42885110163'),
             ('900000103', '312000032', '66820350082'),
             ('900000104', '312000040', '05928210261'),
             ('900000104', '312000040', '16834910156'),
             ('900000104', '312000040', '14899610025'),
             ('900000104', '312000040', '69906310147'),
             ('900000104', '312000040', '31857810085'),
             ('900000104', '312000040', '49819110351'),
             ('900000104', '312000040', '21865810048'),
             ('900000104', '312000040', '13841050147')) AS v (hpr, orgnummer, personident)
ON CONFLICT (pasient_id) DO UPDATE SET fastlegeliste_id = EXCLUDED.fastlegeliste_id;

WITH inserted AS (
INSERT INTO konsultasjon (id, pasient_id, legekontor_id, startet_tidspunkt, avsluttet_tidspunkt, status,
                          problemstilling)
SELECT md5('hackathon-konsultasjon-' || v.key)::uuid,
       p.id,
       fl.legekontor_id,
       v.startet::timestamp AT TIME ZONE 'Europe/Oslo',
       v.avsluttet::timestamp AT TIME ZONE 'Europe/Oslo',
       v.status,
       v.problemstilling
FROM (VALUES ('BB1', '20856210112', '2026-03-10 09:00', '2026-03-10 09:30', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('BB2', '20856210112', '2026-06-16 09:00', '2026-06-16 09:30', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('BB3', '20856210112', '2026-09-22 09:00', '2026-09-22 09:30', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('BB4', '20856210112', '2026-10-20 08:30', NULL, 'PÅGÅENDE', 'Kontroll av blodtrykk og vekt'),
             ('IB1', '03916410017', '2026-01-14 10:00', '2026-01-14 10:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('IB2', '03916410017', '2026-04-15 10:00', '2026-04-15 10:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('IB3', '03916410017', '2026-07-08 10:00', '2026-07-08 10:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('IB4', '03916410017', '2026-09-30 10:00', '2026-09-30 10:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('TL1', '28815510331', '2026-05-12 13:00', '2026-05-12 13:20', 'FULLFØRT', 'Astma, vedlikeholdsbehandling'),
             ('TL2', '28815510331', '2026-08-01 13:00', NULL, 'AVLYST', 'Astma, kontroll'),
             ('ES1', '04845310039', '2026-02-03 11:00', '2026-02-03 11:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('ES2', '04845310039', '2026-05-26 11:00', '2026-05-26 11:30', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('ES3', '04845310039', '2026-10-20 09:00', NULL, 'PÅGÅENDE', 'Diabetes type 2, kontroll'),
             ('JS1', '14828010132', '2026-08-19 14:00', '2026-08-19 14:25', 'FULLFØRT', 'Ryggsmerter'),
             ('HN1', '65866910027', '2026-04-07 15:00', '2026-04-07 15:45', 'FULLFØRT', 'Nedstemthet'),
             ('HN2', '65866910027', '2026-09-08 15:00', '2026-09-08 15:45', 'FULLFØRT', 'Depresjon, oppfølging'),
             ('SB1', '47876010166', '2026-02-17 08:30', '2026-02-17 09:00', 'FULLFØRT', 'Høyt blodtrykk'),
             ('SB2', '47876010166', '2026-06-02 08:30', '2026-06-02 09:00', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('SB3', '47876010166', '2026-09-15 08:30', '2026-09-15 09:00', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('AF1', '17819010269', '2026-09-29 12:00', '2026-09-29 12:20', 'FULLFØRT', 'Forkjølelse og sår hals'),
             ('GF1', '23895710156', '2026-01-20 09:30', '2026-01-20 10:00', 'FULLFØRT', 'Diabetes og høyt blodtrykk'),
             ('GF2', '23895710156', '2026-04-21 09:30', '2026-04-21 10:00', 'FULLFØRT', 'Diabetes og høyt blodtrykk, kontroll'),
             ('GF3', '23895710156', '2026-07-14 09:30', '2026-07-14 10:00', 'FULLFØRT', 'Diabetes og høyt blodtrykk, kontroll'),
             ('GF4', '23895710156', '2026-10-06 09:30', '2026-10-06 10:00', 'FULLFØRT', 'Diabetes og høyt blodtrykk, kontroll'),
             ('GF5', '23895710156', '2026-11-03 09:30', NULL, 'PLANLAGT', 'Diabetes og høyt blodtrykk, kontroll'),
             ('EK1', '30860850180', '2024-06-28 14:00', '2024-06-28 14:30', 'FULLFØRT', 'Helsekontroll'),
             ('EK2', '30860850180', '2025-06-27 14:00', '2025-06-27 14:30', 'FULLFØRT', 'Helsekontroll'),
             ('EK3', '30860850180', '2026-06-29 14:00', '2026-06-29 14:30', 'FULLFØRT', 'Helsekontroll'),
             ('TN1', '59918610175', '2026-10-20 10:00', NULL, 'PÅGÅENDE', 'Hoste i tre uker'),
             ('MS1', '05928210261', '2026-02-10 16:00', '2026-02-10 16:30', 'FULLFØRT', 'Overvekt, oppfølging'),
             ('MS2', '05928210261', '2026-05-12 16:00', '2026-05-12 16:30', 'FULLFØRT', 'Overvekt, oppfølging'),
             ('MS3', '05928210261', '2026-08-11 16:00', '2026-08-11 16:30', 'FULLFØRT', 'Overvekt, oppfølging'),
             ('MS4', '05928210261', '2026-10-13 16:00', '2026-10-13 16:30', 'FULLFØRT', 'Overvekt, oppfølging'),
             ('HB1', '16834910156', '2026-01-27 08:00', '2026-01-27 08:30', 'FULLFØRT', 'Høyt blodtrykk'),
             ('HB2', '16834910156', '2026-04-28 08:00', '2026-04-28 08:30', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('HB3', '16834910156', '2026-07-21 08:00', '2026-07-21 08:30', 'FULLFØRT', 'Høyt blodtrykk, kontroll'),
             ('HB4', '16834910156', '2026-10-20 11:00', NULL, 'PÅGÅENDE', 'Høyt blodtrykk, kontroll'),
             ('RS1', '69906310147', '2026-03-03 13:30', '2026-03-03 14:00', 'FULLFØRT', 'Diabetes type 2'),
             ('RS2', '69906310147', '2026-06-23 13:30', '2026-06-23 14:00', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('RS3', '69906310147', '2026-09-29 13:30', '2026-09-29 14:00', 'FULLFØRT', 'Diabetes type 2, kontroll'),
             ('LK1', '13841050147', '2026-05-19 15:30', '2026-05-19 15:50', 'FULLFØRT', 'Astma, kontroll'))
         AS v (key, personident, startet, avsluttet, status, problemstilling)
         JOIN pasient p ON p.personident = v.personident
         JOIN fastlegeliste_pasient flp ON flp.pasient_id = p.id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
ON CONFLICT (id) DO NOTHING
RETURNING id, pasient_id)
INSERT INTO konsultasjon_helsepersonell (konsultasjon_id, hpr)
SELECT i.id, fl.hpr
FROM inserted i
         JOIN fastlegeliste_pasient flp ON flp.pasient_id = i.pasient_id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id;

INSERT INTO konsultasjon_diagnosekode (konsultasjon_id, diagnosesystem, diagnosekode)
SELECT k.id, d.diagnosesystem, d.diagnosekode
FROM (VALUES ('BB', 'ICPC2', 'K86'),
             ('BB', 'ICD10', 'I10'),
             ('IB', 'ICPC2', 'T90'),
             ('IB', 'ICD10', 'E11.9'),
             ('TL', 'ICPC2', 'R96'),
             ('TL', 'ICD10', 'J45.9'),
             ('ES', 'ICPC2', 'T90'),
             ('ES', 'ICD10', 'E11.9'),
             ('JS', 'ICPC2', 'L84'),
             ('JS', 'ICD10', 'M54.5'),
             ('HN', 'ICPC2', 'P76'),
             ('HN', 'ICD10', 'F32.1'),
             ('SB', 'ICPC2', 'K86'),
             ('SB', 'ICD10', 'I10'),
             ('AF', 'ICPC2', 'R74'),
             ('AF', 'ICD10', 'J06.9'),
             ('GF', 'ICPC2', 'K86'),
             ('GF', 'ICD10', 'I10'),
             ('GF', 'ICPC2', 'T90'),
             ('GF', 'ICD10', 'E11.9'),
             ('EK', 'ICPC2', 'A98'),
             ('EK', 'ICD10', 'Z00.0'),
             ('TN', 'ICPC2', 'R05'),
             ('TN', 'ICD10', 'R05'),
             ('MS', 'ICPC2', 'T82'),
             ('MS', 'ICD10', 'E66.9'),
             ('HB', 'ICPC2', 'K86'),
             ('HB', 'ICD10', 'I10'),
             ('RS', 'ICPC2', 'T90'),
             ('RS', 'ICD10', 'E11.9'),
             ('LK', 'ICPC2', 'R96'),
             ('LK', 'ICD10', 'J45.9')) AS d (prefix, diagnosesystem, diagnosekode)
         JOIN konsultasjon k ON k.id IN (SELECT md5('hackathon-konsultasjon-' || d.prefix || n)::uuid
                                         FROM generate_series(1, 5) AS n)
WHERE k.status NOT IN ('PLANLAGT', 'AVLYST')
ON CONFLICT DO NOTHING;

INSERT INTO journalnotat (id, konsultasjon_id, pasient_id, journalnotat)
SELECT md5('hackathon-journalnotat-' || v.key)::uuid, k.id, k.pasient_id, v.journalnotat
FROM (VALUES ('BB3', 'Blodtrykk synkende på amlodipin. Fortsetter dagens dose, ny kontroll om tre måneder.'),
             ('IB4', 'HbA1c nær målverdi. Fortsetter metformin og kostholdsråd.'),
             ('JS1', 'Akutte korsryggsmerter uten utstråling. Råd om aktivitet, kontroll ved manglende bedring.'),
             ('HN2', 'Bedring etter oppstart samtaleterapi. Fortsatt sykmeldt 50 prosent.'),
             ('AF1', 'Forkjølelse uten tegn til bakteriell infeksjon. Symptomatisk behandling.'),
             ('LK1', 'God astmakontroll. Videreføres uendret.')) AS v (key, journalnotat)
         JOIN konsultasjon k ON k.id = md5('hackathon-konsultasjon-' || v.key)::uuid
WHERE NOT EXISTS (SELECT 1 FROM journalnotat j WHERE j.konsultasjon_id = k.id)
ON CONFLICT (id) DO NOTHING;

INSERT INTO maaling (id, pasient_id, konsultasjon_id, hpr, loinc_kode, loinc_visningsnavn, verdi, enhet_kode,
                     enhet_visningsnavn, effektiv_tidspunkt, status)
SELECT md5('hackathon-maaling-' || v.key || v.loinc_kode)::uuid,
       k.pasient_id,
       k.id,
       (SELECT fl.hpr
        FROM fastlegeliste_pasient flp
                 JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
        WHERE flp.pasient_id = k.pasient_id),
       v.loinc_kode,
       v.loinc_visningsnavn,
       v.verdi::numeric,
       v.enhet_kode,
       v.enhet_visningsnavn,
       k.startet_tidspunkt,
       'FINAL'
FROM (VALUES ('BB1', '8480-6', 'Systolic blood pressure', '152', 'mm[Hg]', 'mm Hg'),
             ('BB1', '8462-4', 'Diastolic blood pressure', '94', 'mm[Hg]', 'mm Hg'),
             ('BB1', '29463-7', 'Body weight', '94.5', 'kg', 'kg'),
             ('BB2', '8480-6', 'Systolic blood pressure', '146', 'mm[Hg]', 'mm Hg'),
             ('BB2', '8462-4', 'Diastolic blood pressure', '90', 'mm[Hg]', 'mm Hg'),
             ('BB2', '29463-7', 'Body weight', '93.2', 'kg', 'kg'),
             ('BB3', '8480-6', 'Systolic blood pressure', '138', 'mm[Hg]', 'mm Hg'),
             ('BB3', '8462-4', 'Diastolic blood pressure', '86', 'mm[Hg]', 'mm Hg'),
             ('BB3', '29463-7', 'Body weight', '91.8', 'kg', 'kg'),
             ('IB1', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '8.4', '%', '%'),
             ('IB2', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.9', '%', '%'),
             ('IB3', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.4', '%', '%'),
             ('IB4', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.1', '%', '%'),
             ('IB1', '29463-7', 'Body weight', '82.0', 'kg', 'kg'),
             ('IB4', '29463-7', 'Body weight', '79.6', 'kg', 'kg'),
             ('ES1', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '8.9', '%', '%'),
             ('ES2', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '8.1', '%', '%'),
             ('SB1', '8480-6', 'Systolic blood pressure', '168', 'mm[Hg]', 'mm Hg'),
             ('SB1', '8462-4', 'Diastolic blood pressure', '102', 'mm[Hg]', 'mm Hg'),
             ('SB1', '8867-4', 'Heart rate', '78', '/min', '/min'),
             ('SB2', '8480-6', 'Systolic blood pressure', '158', 'mm[Hg]', 'mm Hg'),
             ('SB2', '8462-4', 'Diastolic blood pressure', '96', 'mm[Hg]', 'mm Hg'),
             ('SB2', '8867-4', 'Heart rate', '74', '/min', '/min'),
             ('SB3', '8480-6', 'Systolic blood pressure', '144', 'mm[Hg]', 'mm Hg'),
             ('SB3', '8462-4', 'Diastolic blood pressure', '88', 'mm[Hg]', 'mm Hg'),
             ('SB3', '8867-4', 'Heart rate', '70', '/min', '/min'),
             ('GF1', '8480-6', 'Systolic blood pressure', '160', 'mm[Hg]', 'mm Hg'),
             ('GF1', '8462-4', 'Diastolic blood pressure', '98', 'mm[Hg]', 'mm Hg'),
             ('GF1', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.9', '%', '%'),
             ('GF1', '29463-7', 'Body weight', '102.4', 'kg', 'kg'),
             ('GF2', '8480-6', 'Systolic blood pressure', '150', 'mm[Hg]', 'mm Hg'),
             ('GF2', '8462-4', 'Diastolic blood pressure', '92', 'mm[Hg]', 'mm Hg'),
             ('GF2', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.6', '%', '%'),
             ('GF2', '29463-7', 'Body weight', '100.8', 'kg', 'kg'),
             ('GF3', '8480-6', 'Systolic blood pressure', '142', 'mm[Hg]', 'mm Hg'),
             ('GF3', '8462-4', 'Diastolic blood pressure', '88', 'mm[Hg]', 'mm Hg'),
             ('GF3', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.2', '%', '%'),
             ('GF3', '29463-7', 'Body weight', '99.1', 'kg', 'kg'),
             ('GF4', '8480-6', 'Systolic blood pressure', '136', 'mm[Hg]', 'mm Hg'),
             ('GF4', '8462-4', 'Diastolic blood pressure', '84', 'mm[Hg]', 'mm Hg'),
             ('GF4', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '6.9', '%', '%'),
             ('GF4', '29463-7', 'Body weight', '97.5', 'kg', 'kg'),
             ('EK1', '8302-2', 'Body height', '176.0', 'cm', 'cm'),
             ('EK1', '29463-7', 'Body weight', '62.5', 'kg', 'kg'),
             ('EK2', '8302-2', 'Body height', '179.5', 'cm', 'cm'),
             ('EK2', '29463-7', 'Body weight', '66.0', 'kg', 'kg'),
             ('EK3', '8302-2', 'Body height', '181.0', 'cm', 'cm'),
             ('EK3', '29463-7', 'Body weight', '68.2', 'kg', 'kg'),
             ('MS1', '29463-7', 'Body weight', '88.0', 'kg', 'kg'),
             ('MS1', '39156-5', 'Body mass index (BMI) [Ratio]', '31.2', 'kg/m2', 'kg/m2'),
             ('MS2', '29463-7', 'Body weight', '86.5', 'kg', 'kg'),
             ('MS2', '39156-5', 'Body mass index (BMI) [Ratio]', '30.7', 'kg/m2', 'kg/m2'),
             ('MS3', '29463-7', 'Body weight', '85.1', 'kg', 'kg'),
             ('MS3', '39156-5', 'Body mass index (BMI) [Ratio]', '30.2', 'kg/m2', 'kg/m2'),
             ('MS4', '29463-7', 'Body weight', '83.9', 'kg', 'kg'),
             ('MS4', '39156-5', 'Body mass index (BMI) [Ratio]', '29.8', 'kg/m2', 'kg/m2'),
             ('HB1', '8480-6', 'Systolic blood pressure', '164', 'mm[Hg]', 'mm Hg'),
             ('HB1', '8462-4', 'Diastolic blood pressure', '96', 'mm[Hg]', 'mm Hg'),
             ('HB2', '8480-6', 'Systolic blood pressure', '156', 'mm[Hg]', 'mm Hg'),
             ('HB2', '8462-4', 'Diastolic blood pressure', '92', 'mm[Hg]', 'mm Hg'),
             ('HB3', '8480-6', 'Systolic blood pressure', '148', 'mm[Hg]', 'mm Hg'),
             ('HB3', '8462-4', 'Diastolic blood pressure', '88', 'mm[Hg]', 'mm Hg'),
             ('HB4', '8480-6', 'Systolic blood pressure', '142', 'mm[Hg]', 'mm Hg'),
             ('HB4', '8462-4', 'Diastolic blood pressure', '84', 'mm[Hg]', 'mm Hg'),
             ('RS1', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '9.1', '%', '%'),
             ('RS2', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '8.2', '%', '%'),
             ('RS3', '4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '7.5', '%', '%'))
         AS v (key, loinc_kode, loinc_visningsnavn, verdi, enhet_kode, enhet_visningsnavn)
         JOIN konsultasjon k ON k.id = md5('hackathon-konsultasjon-' || v.key)::uuid
ON CONFLICT (id) DO NOTHING;
