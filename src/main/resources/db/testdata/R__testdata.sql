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

INSERT INTO fastlegeliste (hpr, legekontor_id)
SELECT v.hpr, (SELECT id FROM legekontor WHERE orgnummer = v.orgnummer)
FROM (VALUES ('565501872', '311111116'),
             ('900000001', '311111124')) AS v (hpr, orgnummer)
ON CONFLICT (hpr, legekontor_id) DO NOTHING;

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
             ('900000001', '311111124', '49886810767')) AS v (hpr, orgnummer, personident)
ON CONFLICT (pasient_id) DO UPDATE SET fastlegeliste_id = EXCLUDED.fastlegeliste_id;

DELETE
FROM fastlegeliste fl
WHERE fl.hpr IN ('565501872', '900000001')
  AND fl.legekontor_id NOT IN (SELECT id FROM legekontor WHERE orgnummer IN ('311111116', '311111124'))
  AND NOT EXISTS (SELECT 1 FROM fastlegeliste_pasient flp WHERE flp.fastlegeliste_id = fl.id);

WITH stories (prefix, personident, first_date, offsets, tid, minutter, problemstilling) AS (
    VALUES ('FR', '14838510036', '2023-03-07', '{0,170,340,510,680,850,1020,1200}', '09:00', 30, 'Overvekt og forhøyet blodsukker, kontroll'),
           ('TF', '02867910193', '2023-02-14', '{0,120,240,360,480,600,720,840,960,1080}', '10:00', 30, 'Høyt blodtrykk, kontroll'),
           ('GV', '30898110431', '2023-05-09', '{0,180,360,540,720,900,1080}', '13:00', 30, 'Hypotyreose, kontroll'),
           ('LF', '17841250397', '2022-09-13', '{0,170,340,510,680,850,1020,1200,1380}', '15:30', 20, 'Astma, kontroll'),
           ('SE', '65819210459', '2024-01-16', '{0,60,120,200,290,380,480,590}', '14:00', 45, 'Depresjon, oppfølging'),
           ('RL', '49886810767', '2023-01-17', '{0,150,300,450,600,750,900,1050,1200,1350}', '08:30', 30, 'Diabetes og høyt blodtrykk, kontroll')),
     enkeltvis (key, prefix, seq, personident, dato, tid, minutter, status, problemstilling) AS (
    VALUES ('FRa', 'FR', 9, '14838510036', '2026-10-20', '09:30', 30, 'PÅGÅENDE', 'Overvekt og forhøyet blodsukker, kontroll'),
           ('TFa', 'TF', 11, '02867910193', '2026-10-20', '10:30', 30, 'PÅGÅENDE', 'Høyt blodtrykk, kontroll'),
           ('TFp', 'TFp', 1, '02867910193', '2026-11-17', '10:00', 30, 'PLANLAGT', 'Høyt blodtrykk, kontroll'),
           ('GVa', 'GV', 8, '30898110431', '2026-10-20', '13:30', 30, 'PÅGÅENDE', 'Hypotyreose, kontroll'),
           ('MVa', 'MVa', 1, '03920150631', '2024-09-10', '16:00', 20, 'FULLFØRT', 'Prevensjon'),
           ('MVavl', 'MVavl', 1, '03920150631', '2025-07-01', '16:00', 20, 'AVLYST', 'Helsekontroll, avlyst av pasient'),
           ('MVb', 'MVb', 1, '03920150631', '2025-03-18', '16:00', 20, 'FULLFØRT', 'Svie ved vannlating'),
           ('MVc', 'MVc', 1, '03920150631', '2025-11-11', '16:00', 30, 'FULLFØRT', 'Hodepine'),
           ('MVd', 'MVd', 1, '03920150631', '2026-04-14', '16:00', 20, 'FULLFØRT', 'Sår hals og feber'),
           ('MVe', 'MVe', 1, '03920150631', '2026-10-20', '14:30', 30, 'PÅGÅENDE', 'Hodepine og tretthet'),
           ('APa', 'APa', 1, '21914897936', '2024-03-12', '11:00', 20, 'FULLFØRT', 'Magesmerter og diaré'),
           ('APb', 'APb', 1, '21914897936', '2025-02-04', '11:00', 20, 'FULLFØRT', 'Forkjølelse og hoste'),
           ('APc', 'APc', 1, '21914897936', '2025-11-25', '11:00', 20, 'FULLFØRT', 'Sår hals og feber'),
           ('APd', 'APd', 1, '21914897936', '2026-05-19', '11:00', 30, 'FULLFØRT', 'Helsekontroll'),
           ('APe', 'APe', 1, '21914897936', '2026-10-20', '11:30', 30, 'PÅGÅENDE', 'Ryggsmerter'),
           ('LFa', 'LF', 10, '17841250397', '2026-10-20', '15:30', 20, 'PÅGÅENDE', 'Astma, kontroll'),
           ('LFp', 'LFp', 1, '17841250397', '2026-12-08', '15:30', 20, 'PLANLAGT', 'Astma, kontroll'),
           ('SEa', 'SE', 9, '65819210459', '2026-10-20', '12:00', 45, 'PÅGÅENDE', 'Depresjon, oppfølging'),
           ('RLa', 'RL', 11, '49886810767', '2026-10-20', '08:30', 30, 'PÅGÅENDE', 'Diabetes og høyt blodtrykk, kontroll'),
           ('RLavl', 'RLavl', 1, '49886810767', '2026-03-10', '08:30', 30, 'AVLYST', 'Diabetes type 2, kontroll'),
           ('RLp', 'RLp', 1, '49886810767', '2026-11-10', '08:30', 30, 'PLANLAGT', 'Diabetes og høyt blodtrykk, kontroll')),
     dagsplan AS (
SELECT s.prefix || o.seq AS key, s.prefix, o.seq::int AS seq, s.personident,
       s.first_date::date + o.dagoffset AS dag, s.tid, s.minutter, 'FULLFØRT' AS status, s.problemstilling
FROM stories s
         CROSS JOIN LATERAL unnest(s.offsets::int[]) WITH ORDINALITY AS o (dagoffset, seq)
UNION ALL
SELECT key, prefix, seq, personident, dato::date, tid, minutter, status, problemstilling
FROM enkeltvis),
     plan AS (
SELECT d.key, d.prefix, d.seq, d.personident,
       v.virkedag + d.tid::time AS startet,
       CASE WHEN d.status IN ('AVLYST', 'PLANLAGT', 'PÅGÅENDE') THEN NULL
            ELSE v.virkedag + d.tid::time + make_interval(mins => d.minutter) END AS avsluttet,
       d.status, d.problemstilling
FROM dagsplan d
         CROSS JOIN LATERAL (SELECT d.dag + CASE extract(isodow FROM d.dag) WHEN 6 THEN 2 WHEN 7 THEN 1 ELSE 0 END AS virkedag) v),
     diagnosekoder (prefix, diagnosesystem, diagnosekode) AS (
    VALUES ('FR', 'ICPC2', 'T82'),
           ('FR', 'ICD10', 'E66.9'),
           ('TF', 'ICPC2', 'K86'),
           ('TF', 'ICD10', 'I10'),
           ('GV', 'ICPC2', 'T86'),
           ('GV', 'ICD10', 'E03.9'),
           ('LF', 'ICPC2', 'R96'),
           ('LF', 'ICD10', 'J45.9'),
           ('SE', 'ICPC2', 'P76'),
           ('SE', 'ICD10', 'F32.1'),
           ('RL', 'ICPC2', 'T90'),
           ('RL', 'ICD10', 'E11.9'),
           ('RL', 'ICPC2', 'K86'),
           ('RL', 'ICD10', 'I10'),
           ('MVa', 'ICPC2', 'W11'),
           ('MVa', 'ICD10', 'Z30.0'),
           ('MVb', 'ICPC2', 'U71'),
           ('MVb', 'ICD10', 'N30.0'),
           ('MVc', 'ICPC2', 'N89'),
           ('MVc', 'ICD10', 'G43.9'),
           ('MVd', 'ICPC2', 'R72'),
           ('MVd', 'ICD10', 'J02.0'),
           ('MVe', 'ICPC2', 'N89'),
           ('MVe', 'ICD10', 'G43.9'),
           ('APa', 'ICPC2', 'D73'),
           ('APa', 'ICD10', 'A09'),
           ('APb', 'ICPC2', 'R74'),
           ('APb', 'ICD10', 'J06.9'),
           ('APc', 'ICPC2', 'R72'),
           ('APc', 'ICD10', 'J02.0'),
           ('APd', 'ICPC2', 'A98'),
           ('APd', 'ICD10', 'Z00.0'),
           ('APe', 'ICPC2', 'L84'),
           ('APe', 'ICD10', 'M54.5')),
     notatmaler (prefix, tekster) AS (
    VALUES ('FR', ARRAY['Overvekt og høy livsstilsrisiko. HbA1c i øvre normalområde, kostholdsråd og tilbud om gruppekurs.',
                        'Vekten stiger. HbA1c litt høyere, avtalt mål for aktivitet.',
                        'HbA1c i prediabetesområdet. Fokus på kosthold og daglig gange.',
                        'HbA1c fortsatt stigende og vekt økende. Tettere oppfølging avtalt.',
                        'Ingen vektnedgang. Henvist til frisklivssentral.']),
           ('TF', ARRAY['Gjentatt forhøyet blodtrykk. Hjemmemåling og livsstilsråd, kontroll om fire måneder.',
                        'Blodtrykket fortsatt over mål. Starter ACE-hemmer.',
                        'Noe bedre, men fortsatt over mål. Dosen økt.',
                        'Hjemmemålinger bekrefter høyt blodtrykk. Tillegg av amlodipin vurdert.',
                        'Blodtrykket stiger til tross for behandling. Medisinbruk og salt i kostholdet gjennomgått.']),
           ('GV', ARRAY['Tretthet og kuldeintoleranse. TSH klart forhøyet, starter levotyroksin 75 mikrogram.',
                        'TSH synkende. Dose økt til 100 mikrogram.',
                        'Mer energi. TSH nær normalt, uendret dose.',
                        'TSH innenfor referanseområdet. Kontroll om seks måneder.']),
           ('LF', ARRAY['Astma med anstrengelsesutløst hoste. Spirometri tatt, starter inhalasjonssteroid.',
                        'God effekt. Vekst og lungefunksjon følger forventet utvikling.',
                        'Ingen forverring. Inhalasjonsteknikk gjennomgått.',
                        'Fortsatt god kontroll, deltar i idrett uten plager.']),
           ('SE', ARRAY['Nedstemthet og lav energi. PHQ-9 høy, starter samtaleterapi og sertralin.',
                        'Delvis effekt. Dosen økt, samtaleterapi fortsetter.',
                        'Bedre søvn og stemningsleie. PHQ-9 synkende.',
                        'Tydelig bedring. Tilbake i jobb i redusert stilling.',
                        'Stabil bedring. Nedtrapping av medisin vurderes senere.']),
           ('RL', ARRAY['Type 2 diabetes og hypertensjon. HbA1c og blodtrykk over mål, begge legemidler justert.',
                        'HbA1c stiger. Metformin økt, kostholdsveiledning gjentatt.',
                        'eGFR lett synkende. Nyrefunksjon følges, blodtrykk fortsatt høyt.',
                        'HbA1c og blodtrykk fortsatt over mål. SGLT2-hemmer lagt til.',
                        'Vekten øker og HbA1c er stigende. Ny samtale om kosthold og aktivitet.']),
           ('MVa', ARRAY['Ønsker prevensjon. Alternativer gjennomgått, starter minipille.']),
           ('MVb', ARRAY['Svie og hyppig vannlating. Urinstix positiv, pivmecillinam i tre dager.']),
           ('MVc', ARRAY['Migrene med aura, to anfall siste måned. Starter anfallsbehandling.']),
           ('MVd', ARRAY['Sår hals med feber. Strep A-test positiv, penicillin V i ti dager.']),
           ('MVe', ARRAY['Hodepine og tretthet siste to uker. Normale funn ved undersøkelse, dagbok over anfall.']),
           ('APa', ARRAY['Magesmerter og diaré i to døgn. Antatt viral gastroenteritt, råd om væske.']),
           ('APb', ARRAY['Forkjølelse med hoste. Symptomatisk behandling.']),
           ('APc', ARRAY['Sår hals og feber. Strep A-test positiv, behandlet med penicillin V.']),
           ('APd', ARRAY['Rutinemessig helsekontroll. Normale funn.']),
           ('APe', ARRAY['Korsryggsmerter etter flytting. Ingen utstråling, råd om aktivitet og smertestillende ved behov.'])),
     notat (key, tekst) AS (
SELECT p.key, n.tekster[CASE WHEN p.seq = 1 OR cardinality(n.tekster) = 1 THEN 1
                              ELSE 2 + (p.seq - 2) % (cardinality(n.tekster) - 1) END]
FROM plan p
         JOIN notatmaler n ON n.prefix = p.prefix
WHERE p.status NOT IN ('PLANLAGT', 'AVLYST')),
     loinc (loinc_kode, visningsnavn, enhet_kode, enhet_visningsnavn, desimaler) AS (
    VALUES ('8480-6', 'Systolic blood pressure', 'mm[Hg]', 'mm Hg', 0),
           ('8462-4', 'Diastolic blood pressure', 'mm[Hg]', 'mm Hg', 0),
           ('4548-4', 'Hemoglobin A1c/Hemoglobin.total in Blood', '%', '%', 1),
           ('29463-7', 'Body weight', 'kg', 'kg', 1),
           ('8302-2', 'Body height', 'cm', 'cm', 1),
           ('39156-5', 'Body mass index (BMI) [Ratio]', 'kg/m2', 'kg/m2', 1),
           ('8867-4', 'Heart rate', '/min', '/min', 0),
           ('62238-1', 'Glomerular filtration rate/1.73 sq M.predicted by Creatinine-based formula (CKD-EPI)', 'mL/min/{1.73_m2}', 'mL/min/1.73 m2', 0),
           ('14682-9', 'Creatinine [Moles/volume] in Serum or Plasma', 'umol/L', 'µmol/L', 0),
           ('14647-2', 'Cholesterol [Moles/volume] in Serum or Plasma', 'mmol/L', 'mmol/L', 1),
           ('22748-8', 'Cholesterol in LDL [Moles/volume] in Serum or Plasma', 'mmol/L', 'mmol/L', 1),
           ('3016-3', 'Thyrotropin [Units/volume] in Serum or Plasma', 'm[IU]/L', 'mIU/L', 1),
           ('44261-6', 'Patient Health Questionnaire 9 item total score [Reported]', '{score}', 'poeng', 0),
           ('69737-5', 'Generalized anxiety disorder 7 item total score [Reported]', '{score}', 'poeng', 0),
           ('20150-9', 'Forced expiratory volume in 1 second', 'L', 'L', 2),
           ('59408-5', 'Oxygen saturation in Arterial blood by Pulse oximetry', '%', '%', 0),
           ('1988-5', 'C reactive protein [Mass/volume] in Serum or Plasma', 'mg/L', 'mg/L', 0),
           ('8310-5', 'Body temperature', 'Cel', '°C', 1),
           ('72514-3', 'Pain severity - 0-10 verbal numeric rating [Score] - Reported', '{score}', 'poeng', 0)),
     serier (prefix, loinc_kode, startverdi, endring, svingning) AS (
    VALUES ('FR', '4548-4', 5.5, 0.15, 0.05),
           ('FR', '29463-7', 80.0, 1.1, 0.3),
           ('FR', '8480-6', 118, 1.5, 1.5),
           ('FR', '8462-4', 76, 0.8, 1),
           ('FR', '8867-4', 70, 0, 1.5),
           ('FR', '14647-2', 5.0, 0.15, 0.1),
           ('TF', '8480-6', 148, 1.8, 1.2),
           ('TF', '8462-4', 92, 1.0, 0.8),
           ('TF', '29463-7', 94.0, 0.4, 0.4),
           ('TF', '8867-4', 76, 0, 1.5),
           ('TF', '14647-2', 5.9, 0.08, 0.1),
           ('TF', '22748-8', 3.8, 0.07, 0.08),
           ('GV', '3016-3', 11.2, -1.3, 0.2),
           ('GV', '29463-7', 78.0, -0.8, 0.3),
           ('GV', '8867-4', 74, -0.5, 1.5),
           ('GV', '8480-6', 122, 0, 2),
           ('GV', '8462-4', 78, 0, 1.5),
           ('GV', '14647-2', 5.8, -0.1, 0.1),
           ('LF', '20150-9', 1.80, 0.12, 0.03),
           ('LF', '8302-2', 138.0, 2.7, 0.3),
           ('LF', '29463-7', 32.0, 1.7, 0.3),
           ('LF', '59408-5', 98, 0, 0.5),
           ('LF', '8867-4', 84, -0.5, 1.5),
           ('SE', '44261-6', 21, -2.1, 0.5),
           ('SE', '69737-5', 12, -1.0, 0.4),
           ('SE', '29463-7', 64.0, 0.5, 0.4),
           ('SE', '8867-4', 78, -0.8, 1.2),
           ('SE', '8480-6', 112, 0, 2),
           ('SE', '8462-4', 72, 0, 1.5),
           ('RL', '4548-4', 7.2, 0.17, 0.1),
           ('RL', '62238-1', 82, -1.3, 0.6),
           ('RL', '14682-9', 88, 1.8, 1.5),
           ('RL', '8480-6', 140, 1.5, 1.2),
           ('RL', '8462-4', 86, 0.8, 0.8),
           ('RL', '29463-7', 96.0, 0.6, 0.4),
           ('RL', '14647-2', 5.4, 0.05, 0.1),
           ('RL', '22748-8', 3.4, 0.05, 0.08),
           ('RL', '8867-4', 74, 0, 1.5),
           ('MVa', '29463-7', 61.2, 0, 0),
           ('MVa', '8480-6', 112, 0, 0),
           ('MVa', '8462-4', 70, 0, 0),
           ('MVb', '8310-5', 37.2, 0, 0),
           ('MVc', '8480-6', 116, 0, 0),
           ('MVc', '8462-4', 72, 0, 0),
           ('MVc', '72514-3', 7, 0, 0),
           ('MVd', '1988-5', 58, 0, 0),
           ('MVd', '8310-5', 38.4, 0, 0),
           ('MVe', '29463-7', 62.0, 0, 0),
           ('MVe', '8480-6', 114, 0, 0),
           ('MVe', '8462-4', 72, 0, 0),
           ('MVe', '72514-3', 5, 0, 0),
           ('APa', '8310-5', 37.4, 0, 0),
           ('APb', '1988-5', 12, 0, 0),
           ('APb', '8310-5', 37.8, 0, 0),
           ('APc', '1988-5', 64, 0, 0),
           ('APc', '8310-5', 38.7, 0, 0),
           ('APd', '8302-2', 178.0, 0, 0),
           ('APd', '29463-7', 84.2, 0, 0),
           ('APd', '8480-6', 132, 0, 0),
           ('APd', '8462-4', 84, 0, 0),
           ('APe', '29463-7', 84.9, 0, 0),
           ('APe', '72514-3', 6, 0, 0)),
     seriemaalinger AS (
SELECT p.key,
       s.loinc_kode,
       round(s.startverdi::numeric + s.endring::numeric * (p.seq - 1)
                 + s.svingning::numeric * (((p.seq * 3 + length(p.prefix) * 2 + ascii(right(s.loinc_kode, 1))) % 5) - 2),
             l.desimaler) AS verdi
FROM plan p
         JOIN serier s ON s.prefix = p.prefix
         JOIN loinc l ON l.loinc_kode = s.loinc_kode),
     maalinger (key, loinc_kode, loinc_visningsnavn, verdi, enhet_kode, enhet_visningsnavn) AS (
SELECT m.key, l.loinc_kode, l.visningsnavn, m.verdi, l.enhet_kode, l.enhet_visningsnavn
FROM (SELECT key, loinc_kode, verdi
      FROM seriemaalinger
      UNION ALL
      SELECT v.key, '39156-5', round(v.verdi / power(h.verdi / 100, 2), 1)
      FROM seriemaalinger v
               JOIN seriemaalinger h ON h.key = v.key AND h.loinc_kode = '8302-2'
      WHERE v.loinc_kode = '29463-7'
        AND v.key IN (SELECT key FROM plan WHERE prefix IN ('LF', 'APd'))) m
         JOIN loinc l ON l.loinc_kode = m.loinc_kode),
     inserted AS (
INSERT INTO konsultasjon (id, pasient_id, legekontor_id, startet_tidspunkt, avsluttet_tidspunkt, status,
                          problemstilling)
SELECT md5('testdata-konsultasjon-' || p.key)::uuid,
       pa.id,
       fl.legekontor_id,
       p.startet AT TIME ZONE 'Europe/Oslo',
       p.avsluttet AT TIME ZONE 'Europe/Oslo',
       p.status,
       p.problemstilling
FROM plan p
         JOIN pasient pa ON pa.personident = p.personident
         JOIN fastlegeliste_pasient flp ON flp.pasient_id = pa.id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
ON CONFLICT (id) DO NOTHING
RETURNING id, pasient_id, startet_tidspunkt, status),
     helsepersonell AS (
INSERT INTO konsultasjon_helsepersonell (konsultasjon_id, hpr)
SELECT i.id, fl.hpr
FROM inserted i
         JOIN fastlegeliste_pasient flp ON flp.pasient_id = i.pasient_id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
RETURNING konsultasjon_id),
     diagnoser AS (
INSERT INTO konsultasjon_diagnosekode (konsultasjon_id, diagnosesystem, diagnosekode)
SELECT k.id, d.diagnosesystem, d.diagnosekode
FROM inserted k
         JOIN plan p ON md5('testdata-konsultasjon-' || p.key)::uuid = k.id
         JOIN diagnosekoder d ON d.prefix = p.prefix
WHERE k.status NOT IN ('PLANLAGT', 'AVLYST')
RETURNING konsultasjon_id),
     notater AS (
INSERT INTO journalnotat (id, konsultasjon_id, pasient_id, journalnotat)
SELECT md5('testdata-journalnotat-' || n.key)::uuid, k.id, k.pasient_id, n.tekst
FROM notat n
         JOIN inserted k ON k.id = md5('testdata-konsultasjon-' || n.key)::uuid
RETURNING id)
INSERT INTO maaling (id, pasient_id, konsultasjon_id, hpr, loinc_kode, loinc_visningsnavn, verdi, enhet_kode,
                     enhet_visningsnavn, effektiv_tidspunkt, status)
SELECT md5('testdata-maaling-' || m.key || m.loinc_kode)::uuid,
       k.pasient_id,
       k.id,
       (SELECT fl.hpr
        FROM fastlegeliste_pasient flp
                 JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
        WHERE flp.pasient_id = k.pasient_id),
       m.loinc_kode,
       m.loinc_visningsnavn,
       m.verdi,
       m.enhet_kode,
       m.enhet_visningsnavn,
       k.startet_tidspunkt,
       'FINAL'
FROM maalinger m
         JOIN inserted k ON k.id = md5('testdata-konsultasjon-' || m.key)::uuid;
