ALTER TABLE pasient
    RENAME COLUMN fnr TO personident;

ALTER TABLE pasient
    RENAME CONSTRAINT pasient_fnr_key TO pasient_personident_key;

ALTER TABLE pasient
    ADD COLUMN personident_type TEXT,
    ADD COLUMN birth_date DATE,
    ADD COLUMN gender TEXT,
    ADD CONSTRAINT pasient_personident_type_check
        CHECK (personident_type IN ('FNR', 'DNR')),
    ADD CONSTRAINT pasient_gender_check
        CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'UNKNOWN'));
