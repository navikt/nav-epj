import * as z from "zod";

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

export const OpprettPasientSchema = z.object({
    navn: z.string().min(1, "Navn er påkrevd"),
    fnr: z
        .string()
        .regex(/^\d{11}$/, "Fødselsnummer må bestå av 11 siffer"),
});

export type OpprettPasientRequest = z.infer<typeof OpprettPasientSchema>;

export const KonsultasjonSchema = z.object({
    id: z.string(),
    pasientId: z.string(),
    hpr: z.array(z.string()),
    startetTidspunkt: z.string(),
    avsluttetTidspunkt: z.string().nullable(),
    status: z.string(),
    problemstilling: z.string().nullable(),
    journalnotat: z.string().nullable(),
});

export type Konsultasjon = z.infer<typeof KonsultasjonSchema>;

export const DiagnoseSchema = z.object({
    id: z.string(),
    konsultasjon_id: z.string(),
    diagnosekode: z.string(),
    diagnosesystem: z.string(),
});

export type Diagnose = z.infer<typeof DiagnoseSchema>;
