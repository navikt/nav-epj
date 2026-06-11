import * as z from "zod";

/*
CREATE TABLE pasient
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    legekontor_id UUID        NOT NULL REFERENCES legekontor (id),
    fastlege      UUID        NOT NULL REFERENCES helsepersonell (id),
    navn          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
*/

export const LegekontorSchema = z.object({
  id: z.string(),
  navn: z.string(),
});

export type Legekontor = z.infer<typeof LegekontorSchema>;

export const HelsepersonellSchema = z.object({
  id: z.string(),
  hpr: z.string(),
  navn: z.string(),
});

export type Helsepersonell = z.infer<typeof HelsepersonellSchema>;

export const PasientSchema = z.object({
  id: z.string(),
  navn: z.string(),
});

export type Pasient = z.infer<typeof PasientSchema>;
