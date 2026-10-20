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
