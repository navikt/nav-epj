import * as z from "zod";

export const LegekontorSchema = z.object({
    id: z.string(),
    navn: z.string(),
    orgnummer: z.string().nullish(),
    tlf: z.string().nullish(),
});

export type Legekontor = z.infer<typeof LegekontorSchema>;

export const HelsepersonellSchema = z.object({
    hpr: z.string(),
    legekontorId: z.string(),
    navn: z.string(),
    autorisasjon: z.string(),
});

export type Helsepersonell = z.infer<typeof HelsepersonellSchema>;

export const PasientSchema = z.object({
    id: z.string(),
    fornavn: z.string(),
    etternavn: z.string(),
    personident: z.string(),
    personidentType: z.enum(["FNR", "DNR"]).nullable(),
    birthDate: z.string().nullable(),
    gender: z.enum(["FEMALE", "MALE", "OTHER", "UNKNOWN"]).nullable(),
});

export type Pasient = z.infer<typeof PasientSchema>;

export const OpprettPasientSchema = z.object({
    fornavn: z.string().min(1, "Fornavn er påkrevd"),
    etternavn: z.string().min(1, "Etternavn er påkrevd"),
    personident: z
        .string()
        .regex(/^\d{11}$/, "Fødselsnummer eller D-nummer må bestå av 11 siffer"),
    personidentType: z.enum(["FNR", "DNR"], "Velg om det er fødselsnummer eller D-nummer"),
    birthDate: z
        .string()
        .regex(/^\d{4}-\d{2}-\d{2}$/, "Fødselsdato må være en gyldig dato"),
    gender: z.enum(["FEMALE", "MALE", "OTHER", "UNKNOWN"], "Velg kjønn"),
});

export type OpprettPasientRequest = z.infer<typeof OpprettPasientSchema>;

export const JournalnotatEntrySchema = z.object({
    id: z.string(),
    konsultasjonId: z.string(),
    pasientId: z.string(),
    journalnotat: z.string().nullable(),
});

export type JournalnotatEntry = z.infer<typeof JournalnotatEntrySchema>;

export const DiagnoseSchema = z.object({
    code: z.string(),
    system: z.string(),
    text: z.string(),
});

export const KonsultasjonSchema = z.object({
    id: z.string(),
    pasientId: z.string(),
    hpr: z.array(z.string()),
    journalnotat: z.array(JournalnotatEntrySchema),
    diagnoser: z.array(DiagnoseSchema),
    startetTidspunkt: z.string(),
    avsluttetTidspunkt: z.string().nullable(),
    status: z.string(),
    problemstilling: z.string().nullable(),
});

export type Konsultasjon = z.infer<typeof KonsultasjonSchema>;

export type Diagnose = z.infer<typeof DiagnoseSchema>;

export const LaunchModeSchema = z.enum(["iframe", "tab", "ask"]);

export type LaunchMode = z.infer<typeof LaunchModeSchema>;

export const AppSchema = z.object({
    clientId: z.string(),
    navn: z.string(),
    beskrivelse: z.string().nullish(),
    ikon: z.string(),
    launchMode: LaunchModeSchema,
    launchUri: z.string().nullable(),
    tokenEndpointAuthMethod: z.string().nullish(),
    jwksUri: z.string().nullish(),
    redirectUris: z.array(z.string()),
    scopes: z.array(z.string()),
});

export type App = z.infer<typeof AppSchema>;

export const LaunchResponseSchema = z.object({
    launchUrl: z.string(),
});

export const ActivePatientSchema = z.object({
    patientId: z.string(),
    expiresAt: z.string(),
});

export type ActivePatient = z.infer<typeof ActivePatientSchema>;

export const LaunchErrorCodeSchema = z.enum([
    "NO_ACTIVE_PATIENT",
    "NO_ACTIVE_ENCOUNTER",
    "UNKNOWN_APP",
    "PATIENT_MISMATCH",
]);

export const LaunchErrorSchema = z.object({
    code: LaunchErrorCodeSchema,
    message: z.string(),
    appId: z.string().nullish(),
});
