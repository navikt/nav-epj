CREATE TABLE konsultasjon_diagnosekode
(
    konsultasjon_id UUID NOT NULL REFERENCES konsultasjon (id),
    diagnosesystem  TEXT NOT NULL,
    diagnosekode    TEXT NOT NULL,
    PRIMARY KEY (konsultasjon_id, diagnosesystem, diagnosekode)
);

INSERT INTO konsultasjon_diagnosekode (konsultasjon_id, diagnosesystem, diagnosekode)
SELECT DISTINCT konsultasjon_id, diagnosesystem, diagnosekode
FROM diagnose;

DROP TABLE diagnose;
