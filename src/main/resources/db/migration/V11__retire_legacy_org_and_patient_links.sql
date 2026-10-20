DROP TABLE pasient_helsepersonell;

ALTER TABLE pasient
    DROP COLUMN legekontor_id;

ALTER TABLE helsepersonell
    DROP COLUMN legekontor_id;
