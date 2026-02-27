CREATE TABLE patient
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    ident       VARCHAR(11)  NOT NULL UNIQUE,
    first_name  VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name   VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
