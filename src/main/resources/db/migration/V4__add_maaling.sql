CREATE TABLE maaling
(
    id                 UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    pasient_id         UUID           NOT NULL REFERENCES pasient (id),
    konsultasjon_id    UUID           NOT NULL REFERENCES konsultasjon (id),
    hpr                TEXT,
    loinc_kode         TEXT           NOT NULL,
    loinc_visningsnavn TEXT           NOT NULL,
    verdi              NUMERIC(12, 4) NOT NULL,
    enhet_kode         TEXT           NOT NULL,
    enhet_visningsnavn TEXT           NOT NULL,
    effektiv_tidspunkt TIMESTAMPTZ    NOT NULL,
    status             TEXT           NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT maaling_status_check
        CHECK (status IN
               ('REGISTERED', 'PRELIMINARY', 'FINAL', 'AMENDED', 'CORRECTED', 'CANCELLED',
                'ENTERED_IN_ERROR', 'UNKNOWN'))
);

CREATE INDEX maaling_pasient_id_idx ON maaling (pasient_id);
CREATE INDEX maaling_konsultasjon_id_idx ON maaling (konsultasjon_id);
