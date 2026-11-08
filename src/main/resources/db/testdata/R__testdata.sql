INSERT INTO clinic (id, name, phone, org_number)
VALUES ('aed5c75c-3b12-4652-83d7-223bdd69062d', 'Utstrakt Bark', '12345678', '864425208'),
       (gen_random_uuid(), 'Fjordvik Legekontor', '22000001', '311111116'),
       (gen_random_uuid(), 'Lyngdal Legesenter', '22000002', '311111124')
ON CONFLICT (org_number) DO UPDATE
    SET name       = EXCLUDED.name,
        phone      = EXCLUDED.phone,
        updated_at = now();

INSERT INTO practitioner (hpr, name, qualification)
VALUES ('565501872', 'GRØNN VITS', 'Lege')
ON CONFLICT (hpr) DO NOTHING;

INSERT INTO patient (given_name, family_name, personident, personident_type, birth_date, gender)
SELECT v.given_name,
       v.family_name,
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
         AS v (given_name, family_name, personident, personident_type, birth_date, gender)
ON CONFLICT (personident) DO UPDATE
    SET given_name       = EXCLUDED.given_name,
        family_name      = EXCLUDED.family_name,
        personident_type = EXCLUDED.personident_type,
        birth_date       = EXCLUDED.birth_date,
        gender           = EXCLUDED.gender,
        updated_at       = now();

INSERT INTO fastlegeliste (hpr, clinic_id)
SELECT v.hpr, (SELECT id FROM clinic WHERE org_number = v.org_number)
FROM (VALUES ('565501872', '311111116'),
             ('900000001', '311111124')) AS v (hpr, org_number)
ON CONFLICT (hpr, clinic_id) DO NOTHING;

INSERT INTO fastlegeliste_patient (fastlegeliste_id, patient_id)
SELECT (SELECT fl.id
        FROM fastlegeliste fl
                 JOIN clinic cl ON cl.id = fl.clinic_id
        WHERE fl.hpr = v.hpr
          AND cl.org_number = v.org_number),
       (SELECT id FROM patient WHERE personident = v.personident)
FROM (VALUES ('565501872', '311111116', '21914897936'),
             ('565501872', '311111116', '14838510036'),
             ('565501872', '311111116', '02867910193'),
             ('565501872', '311111116', '30898110431'),
             ('565501872', '311111116', '03920150631'),
             ('900000001', '311111124', '17841250397'),
             ('900000001', '311111124', '65819210459'),
             ('900000001', '311111124', '49886810767')) AS v (hpr, org_number, personident)
ON CONFLICT (patient_id) DO UPDATE SET fastlegeliste_id = EXCLUDED.fastlegeliste_id;

DELETE
FROM fastlegeliste fl
WHERE fl.hpr IN ('565501872', '900000001')
  AND fl.clinic_id NOT IN (SELECT id FROM clinic WHERE org_number IN ('311111116', '311111124'))
  AND NOT EXISTS (SELECT 1 FROM fastlegeliste_patient flp WHERE flp.fastlegeliste_id = fl.id);

CREATE TEMP TABLE plan ON COMMIT DROP AS
WITH stories (prefix, personident, first_date, offsets, start_time, duration_minutes, reason_for_visit) AS (
    VALUES ('FR', '14838510036', '2023-03-07', '{0,170,340,510,680,850,1020,1200}', '09:00', 30, 'Overvekt og forhøyet blodsukker, kontroll'),
           ('TF', '02867910193', '2023-02-14', '{0,120,240,360,480,600,720,840,960,1080}', '10:00', 30, 'Høyt blodtrykk, kontroll'),
           ('GV', '30898110431', '2023-05-09', '{0,180,360,540,720,900,1080}', '13:00', 30, 'Hypotyreose, kontroll'),
           ('LF', '17841250397', '2022-09-13', '{0,170,340,510,680,850,1020,1200,1380}', '15:30', 20, 'Astma, kontroll'),
           ('SE', '65819210459', '2024-01-16', '{0,60,120,200,290,380,480,590}', '14:00', 45, 'Depresjon, oppfølging'),
           ('RL', '49886810767', '2023-01-17', '{0,150,300,450,600,750,900,1050,1200,1350}', '08:30', 30, 'Diabetes og høyt blodtrykk, kontroll')),
     single_visits (key, prefix, seq, personident, visit_date, start_time, duration_minutes, status, reason_for_visit) AS (
    VALUES ('FRa', 'FR', 9, '14838510036', '2026-10-20', '09:30', 30, 'IN_PROGRESS', 'Overvekt og forhøyet blodsukker, kontroll'),
           ('TFa', 'TF', 11, '02867910193', '2026-10-20', '10:30', 30, 'IN_PROGRESS', 'Høyt blodtrykk, kontroll'),
           ('TFp', 'TFp', 1, '02867910193', '2026-11-17', '10:00', 30, 'PLANNED', 'Høyt blodtrykk, kontroll'),
           ('GVa', 'GV', 8, '30898110431', '2026-10-20', '13:30', 30, 'IN_PROGRESS', 'Hypotyreose, kontroll'),
           ('MVa', 'MVa', 1, '03920150631', '2024-09-10', '16:00', 20, 'FINISHED', 'Prevensjon'),
           ('MVavl', 'MVavl', 1, '03920150631', '2025-07-01', '16:00', 20, 'CANCELLED', 'Helsekontroll, avlyst av pasient'),
           ('MVb', 'MVb', 1, '03920150631', '2025-03-18', '16:00', 20, 'FINISHED', 'Svie ved vannlating'),
           ('MVc', 'MVc', 1, '03920150631', '2025-11-11', '16:00', 30, 'FINISHED', 'Hodepine'),
           ('MVd', 'MVd', 1, '03920150631', '2026-04-14', '16:00', 20, 'FINISHED', 'Sår hals og feber'),
           ('MVe', 'MVe', 1, '03920150631', '2026-10-20', '14:30', 30, 'IN_PROGRESS', 'Hodepine og tretthet'),
           ('APa', 'APa', 1, '21914897936', '2024-03-12', '11:00', 20, 'FINISHED', 'Magesmerter og diaré'),
           ('APb', 'APb', 1, '21914897936', '2025-02-04', '11:00', 20, 'FINISHED', 'Forkjølelse og hoste'),
           ('APc', 'APc', 1, '21914897936', '2025-11-25', '11:00', 20, 'FINISHED', 'Sår hals og feber'),
           ('APd', 'APd', 1, '21914897936', '2026-05-19', '11:00', 30, 'FINISHED', 'Helsekontroll'),
           ('APe', 'APe', 1, '21914897936', '2026-10-20', '11:30', 30, 'IN_PROGRESS', 'Ryggsmerter'),
           ('LFa', 'LF', 10, '17841250397', '2026-10-20', '15:30', 20, 'IN_PROGRESS', 'Astma, kontroll'),
           ('LFp', 'LFp', 1, '17841250397', '2026-12-08', '15:30', 20, 'PLANNED', 'Astma, kontroll'),
           ('SEa', 'SE', 9, '65819210459', '2026-10-20', '12:00', 45, 'IN_PROGRESS', 'Depresjon, oppfølging'),
           ('RLa', 'RL', 11, '49886810767', '2026-10-20', '08:30', 30, 'IN_PROGRESS', 'Diabetes og høyt blodtrykk, kontroll'),
           ('RLavl', 'RLavl', 1, '49886810767', '2026-03-10', '08:30', 30, 'CANCELLED', 'Diabetes type 2, kontroll'),
           ('RLp', 'RLp', 1, '49886810767', '2026-11-10', '08:30', 30, 'PLANNED', 'Diabetes og høyt blodtrykk, kontroll')),
     schedule AS (
SELECT s.prefix || o.seq AS key, s.prefix, o.seq::int AS seq, s.personident,
       s.first_date::date + o.day_offset AS visit_day, s.start_time, s.duration_minutes, 'FINISHED' AS status, s.reason_for_visit
FROM stories s
         CROSS JOIN LATERAL unnest(s.offsets::int[]) WITH ORDINALITY AS o (day_offset, seq)
UNION ALL
SELECT key, prefix, seq, personident, visit_date::date, start_time, duration_minutes, status, reason_for_visit
FROM single_visits)
SELECT d.key, d.prefix, d.seq, d.personident,
       v.business_day + d.start_time::time AS started_at,
       CASE WHEN d.status IN ('PLANNED', 'IN_PROGRESS') THEN NULL
            ELSE v.business_day + d.start_time::time + make_interval(mins => d.duration_minutes) END AS ended_at,
       d.status, d.reason_for_visit
FROM schedule d
         CROSS JOIN LATERAL (SELECT d.visit_day + CASE extract(isodow FROM d.visit_day) WHEN 6 THEN 2 WHEN 7 THEN 1 ELSE 0 END AS business_day) v;

CREATE TEMP TABLE diagnosis_codes ON COMMIT DROP AS
SELECT v.prefix, v.rank::smallint AS rank, v.diagnosis_system, v.diagnosis_code
FROM (VALUES ('FR', 1, 'ICPC2', 'T82'),
           ('TF', 1, 'ICPC2', 'K86'),
           ('GV', 1, 'ICPC2', 'T86'),
           ('LF', 1, 'ICPC2', 'R96'),
           ('SE', 1, 'ICPC2', 'P76'),
           ('RL', 1, 'ICPC2', 'T90'),
           ('RL', 2, 'ICPC2', 'K86'),
           ('MVa', 1, 'ICPC2', 'W11'),
           ('MVb', 1, 'ICPC2', 'U71'),
           ('MVc', 1, 'ICPC2', 'N89'),
           ('MVd', 1, 'ICPC2', 'R72'),
           ('MVe', 1, 'ICPC2', 'N89'),
           ('APa', 1, 'ICPC2', 'D73'),
           ('APb', 1, 'ICPC2', 'R74'),
           ('APc', 1, 'ICPC2', 'R72'),
           ('APd', 1, 'ICPC2', 'A98'),
           ('APe', 1, 'ICPC2', 'L84')) AS v (prefix, rank, diagnosis_system, diagnosis_code);

CREATE TEMP TABLE legacy_diagnosis_twin ON COMMIT DROP AS
SELECT d.consultation_id, d.diagnosis_code
FROM plan p
         JOIN (VALUES ('FR', 'E66.9'),
                    ('TF', 'I10'),
                    ('GV', 'E03.9'),
                    ('LF', 'J45.9'),
                    ('SE', 'F32.1'),
                    ('RL', 'E11.9'),
                    ('RL', 'I10'),
                    ('MVa', 'Z30.0'),
                    ('MVb', 'N30.0'),
                    ('MVc', 'G43.9'),
                    ('MVd', 'J02.0'),
                    ('MVe', 'G43.9'),
                    ('APa', 'A09.9'),
                    ('APb', 'J06.9'),
                    ('APc', 'J02.0'),
                    ('APd', 'Z00.0'),
                    ('APe', 'M54.5'),
                    ('APa', 'A09')) AS t (prefix, diagnosis_code)
              ON t.prefix = p.prefix
         JOIN consultation_diagnosis_code d
              ON d.consultation_id = md5('testdata-konsultasjon-' || p.key)::uuid
                  AND d.diagnosis_system = 'ICD10'
                  AND d.diagnosis_code = t.diagnosis_code;

DELETE
FROM consultation_diagnosis_code d
    USING legacy_diagnosis_twin l
WHERE d.consultation_id = l.consultation_id
  AND d.diagnosis_system = 'ICD10'
  AND d.diagnosis_code = l.diagnosis_code;

UPDATE consultation_diagnosis_code
SET rank = rank + 1000
WHERE consultation_id IN (SELECT consultation_id FROM legacy_diagnosis_twin);

UPDATE consultation_diagnosis_code d
SET rank = ranked.new_rank
FROM (SELECT d2.consultation_id,
             d2.diagnosis_system,
             d2.diagnosis_code,
             row_number() OVER (PARTITION BY d2.consultation_id ORDER BY s.rank NULLS LAST, d2.rank) AS new_rank
      FROM consultation_diagnosis_code d2
               JOIN plan p ON md5('testdata-konsultasjon-' || p.key)::uuid = d2.consultation_id
               LEFT JOIN diagnosis_codes s
                         ON s.prefix = p.prefix
                             AND s.diagnosis_system = d2.diagnosis_system
                             AND s.diagnosis_code = d2.diagnosis_code
      WHERE d2.consultation_id IN (SELECT consultation_id FROM legacy_diagnosis_twin)) ranked
WHERE d.consultation_id = ranked.consultation_id
  AND d.diagnosis_system = ranked.diagnosis_system
  AND d.diagnosis_code = ranked.diagnosis_code;

WITH note_templates (prefix, texts) AS (
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
     note_plan (key, note_text) AS (
SELECT p.key, n.texts[CASE WHEN p.seq = 1 OR cardinality(n.texts) = 1 THEN 1
                            ELSE 2 + (p.seq - 2) % (cardinality(n.texts) - 1) END]
FROM plan p
         JOIN note_templates n ON n.prefix = p.prefix
WHERE p.status NOT IN ('PLANNED', 'CANCELLED')),
     loinc (loinc_code, display_name, unit_code, unit_display, decimals) AS (
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
     series (prefix, loinc_code, start_value, change, variation) AS (
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
     series_measurements AS (
SELECT p.key,
       s.loinc_code,
       round(s.start_value::numeric + s.change::numeric * (p.seq - 1)
                 + s.variation::numeric * (((p.seq * 3 + length(p.prefix) * 2 + ascii(right(s.loinc_code, 1))) % 5) - 2),
             l.decimals) AS value
FROM plan p
         JOIN series s ON s.prefix = p.prefix
         JOIN loinc l ON l.loinc_code = s.loinc_code),
     measurements (key, loinc_code, loinc_display, value, unit_code, unit_display) AS (
SELECT m.key, l.loinc_code, l.display_name, m.value, l.unit_code, l.unit_display
FROM (SELECT key, loinc_code, value
      FROM series_measurements
      UNION ALL
      SELECT v.key, '39156-5', round(v.value / power(h.value / 100, 2), 1)
      FROM series_measurements v
               JOIN series_measurements h ON h.key = v.key AND h.loinc_code = '8302-2'
      WHERE v.loinc_code = '29463-7'
        AND v.key IN (SELECT key FROM plan WHERE prefix IN ('LF', 'APd'))) m
         JOIN loinc l ON l.loinc_code = m.loinc_code),
     inserted AS (
INSERT INTO consultation (id, patient_id, clinic_id, started_at, ended_at, status,
                          reason_for_visit)
SELECT md5('testdata-konsultasjon-' || p.key)::uuid,
       pt.id,
       fl.clinic_id,
       p.started_at AT TIME ZONE 'Europe/Oslo',
       p.ended_at AT TIME ZONE 'Europe/Oslo',
       p.status,
       p.reason_for_visit
FROM plan p
         JOIN patient pt ON pt.personident = p.personident
         JOIN fastlegeliste_patient flp ON flp.patient_id = pt.id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
ON CONFLICT (id) DO NOTHING
RETURNING id, patient_id, started_at, status),
     practitioner AS (
INSERT INTO consultation_practitioner (consultation_id, hpr)
SELECT i.id, fl.hpr
FROM inserted i
         JOIN fastlegeliste_patient flp ON flp.patient_id = i.patient_id
         JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
RETURNING consultation_id),
     diagnoses AS (
INSERT INTO consultation_diagnosis_code (consultation_id, diagnosis_system, diagnosis_code, rank)
SELECT c.id, d.diagnosis_system, d.diagnosis_code,
       d.rank
FROM inserted c
         JOIN plan p ON md5('testdata-konsultasjon-' || p.key)::uuid = c.id
         JOIN diagnosis_codes d ON d.prefix = p.prefix
WHERE c.status NOT IN ('PLANNED', 'CANCELLED')
RETURNING consultation_id),
     clinical_notes AS (
INSERT INTO clinical_note (id, consultation_id, patient_id, clinical_note)
SELECT md5('testdata-journalnotat-' || n.key)::uuid, c.id, c.patient_id, n.note_text
FROM note_plan n
         JOIN inserted c ON c.id = md5('testdata-konsultasjon-' || n.key)::uuid
RETURNING id)
INSERT INTO measurement (id, patient_id, consultation_id, hpr, loinc_code, loinc_display, value, unit_code,
                         unit_display, effective_at, status)
SELECT md5('testdata-maaling-' || m.key || m.loinc_code)::uuid,
       c.patient_id,
       c.id,
       (SELECT fl.hpr
        FROM fastlegeliste_patient flp
                 JOIN fastlegeliste fl ON fl.id = flp.fastlegeliste_id
        WHERE flp.patient_id = c.patient_id),
       m.loinc_code,
       m.loinc_display,
       m.value,
       m.unit_code,
       m.unit_display,
       c.started_at,
       'FINAL'
FROM measurements m
         JOIN inserted c ON c.id = md5('testdata-konsultasjon-' || m.key)::uuid;

UPDATE consultation
SET ended_at = started_at
WHERE status = 'CANCELLED'
  AND ended_at IS NULL;
