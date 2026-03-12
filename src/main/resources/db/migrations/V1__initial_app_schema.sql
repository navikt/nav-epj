CREATE TABLE patient
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    ident      TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE apps
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    launch_url TEXT NOT NULL
);

INSERT INTO patient (ident, name)
VALUES ('123456789', 'John Doe');

INSERT INTO apps (name, launch_url)
VALUES ('Localhost', 'http://localhost:3000/fhir');

INSERT INTO apps (name, launch_url)
VALUES ('Nav Sykmelding', 'https://www.ekstern.dev.nav.no/samarbeidspartner/sykmelding/fhir');
