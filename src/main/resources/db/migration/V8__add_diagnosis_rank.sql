ALTER TABLE consultation_diagnosis_code
    ADD COLUMN rank SMALLINT;

UPDATE consultation_diagnosis_code d
SET rank = ranked.rank
FROM (SELECT consultation_id,
             diagnosis_system,
             diagnosis_code,
             ROW_NUMBER() OVER (
                 PARTITION BY consultation_id
                 ORDER BY (diagnosis_system = 'ICPC2') DESC, diagnosis_system, diagnosis_code
                 ) AS rank
      FROM consultation_diagnosis_code) ranked
WHERE d.consultation_id = ranked.consultation_id
  AND d.diagnosis_system = ranked.diagnosis_system
  AND d.diagnosis_code = ranked.diagnosis_code;

ALTER TABLE consultation_diagnosis_code
    ALTER COLUMN rank SET NOT NULL,
    ADD CONSTRAINT consultation_diagnosis_code_rank_positive CHECK (rank > 0),
    ADD CONSTRAINT consultation_diagnosis_code_consultation_rank_key UNIQUE (consultation_id, rank);
