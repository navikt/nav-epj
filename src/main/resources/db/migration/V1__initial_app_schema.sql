CREATE TABLE legekontor
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    navn       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE helsepersonell
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    hpr           TEXT        NOT NULL UNIQUE,
    helseid_sub   TEXT UNIQUE, -- oppdateres ved login
    navn          TEXT        NOT NULL,
    autorisasjon  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pasient
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    fastlege      UUID        NOT NULL REFERENCES helsepersonell (id),
    navn          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed-data: legekontor
INSERT INTO legekontor (id, navn)
VALUES ('a1000000-0000-0000-0000-000000000001', 'Tonsberg Legesenter');

-- Seed-data: helsepersonell
INSERT INTO helsepersonell (id, legekontor_id, hpr, navn, autorisasjon)
VALUES ('b2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', '1234567',
        'Dr. Ola Nordmann', 'Lege'),
       ('b2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', '7654321',
        'Dr. Kari Hansen', 'Lege'),
       ('b2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', '9876543',
        'Dr. Per Andersen', 'Spesialist');

-- Seed-data: pasient
INSERT INTO pasient (id, legekontor_id, fastlege, navn)
VALUES ('c3000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000001', 'Kari Testesen'),
       ('c3000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000002', 'Lars Larsen'),
       ('c3000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001',
        'b2000000-0000-0000-0000-000000000003', 'Nora Nansen');





