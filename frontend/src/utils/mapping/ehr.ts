import * as z from "zod";

export const ClinicSchema = z.object({
    id: z.string(),
    name: z.string(),
    orgNumber: z.string().nullish(),
    phone: z.string().nullish(),
});

export type Clinic = z.infer<typeof ClinicSchema>;

export const SessionOrgSchema = z.object({
    org: ClinicSchema.nullable(),
    needsChoice: z.boolean(),
    canChoose: z.boolean(),
    choosableOrgs: z.array(
        z.object({ org: ClinicSchema, hasOwnList: z.boolean() }),
    ),
});

export type SessionOrg = z.infer<typeof SessionOrgSchema>;

export const PractitionerSchema = z.object({
    hpr: z.string(),
    clinicId: z.string().nullish(),
    name: z.string(),
    qualification: z.string(),
});

export type Practitioner = z.infer<typeof PractitionerSchema>;

export const PatientSchema = z.object({
    id: z.string(),
    givenName: z.string(),
    familyName: z.string(),
    personident: z.string(),
    personidentType: z.enum(["FNR", "DNR"]).nullable(),
    birthDate: z.string().nullable(),
    gender: z.enum(["FEMALE", "MALE", "OTHER", "UNKNOWN"]).nullable(),
});

export type Patient = z.infer<typeof PatientSchema>;

export const LatestConsultationSchema = z.object({
    status: z.string(),
    timestamp: z.string(),
});

export const PatientListItemSchema = PatientSchema.extend({
    selectable: z.boolean(),
    latestConsultation: LatestConsultationSchema.nullish(),
});

export type PatientListItem = z.infer<typeof PatientListItemSchema>;

export const CreatePatientSchema = z.object({
    givenName: z.string().min(1, "Fornavn er påkrevd"),
    familyName: z.string().min(1, "Etternavn er påkrevd"),
    personident: z
        .string()
        .regex(/^\d{11}$/, "Fødselsnummer eller D-nummer må bestå av 11 siffer"),
    personidentType: z.enum(["FNR", "DNR"], "Velg om det er fødselsnummer eller D-nummer"),
    birthDate: z
        .string()
        .regex(/^\d{4}-\d{2}-\d{2}$/, "Fødselsdato må være en gyldig dato"),
    gender: z.enum(["FEMALE", "MALE", "OTHER", "UNKNOWN"], "Velg kjønn"),
});

export type CreatePatientRequest = z.infer<typeof CreatePatientSchema>;

export const ClinicalNoteEntrySchema = z.object({
    id: z.string(),
    consultationId: z.string(),
    patientId: z.string(),
    clinicalNote: z.string().nullable(),
    attachmentTitle: z.string().nullable().optional(),
    hasAttachment: z.boolean().optional(),
});

export type ClinicalNoteEntry = z.infer<typeof ClinicalNoteEntrySchema>;

export const DiagnosisSchema = z.object({
    code: z.string(),
    system: z.string(),
    text: z.string(),
});

export const ConsultationSchema = z.object({
    id: z.string(),
    patientId: z.string(),
    hpr: z.array(z.string()),
    clinicalNotes: z.array(ClinicalNoteEntrySchema),
    diagnoses: z.array(DiagnosisSchema),
    startedAt: z.string(),
    endedAt: z.string().nullable(),
    status: z.string(),
    reasonForVisit: z.string().nullable(),
});

export type Consultation = z.infer<typeof ConsultationSchema>;

export type Diagnosis = z.infer<typeof DiagnosisSchema>;

export const MeasurementStatusSchema = z.enum([
    "REGISTERED",
    "PRELIMINARY",
    "FINAL",
    "AMENDED",
    "CORRECTED",
    "CANCELLED",
    "ENTERED_IN_ERROR",
    "UNKNOWN",
]);

export const MeasurementSchema = z.object({
    id: z.string(),
    patientId: z.string(),
    consultationId: z.string(),
    loincCode: z.string(),
    loincDisplay: z.string(),
    value: z.number(),
    unitCode: z.string(),
    unitDisplay: z.string(),
    effectiveAt: z.string(),
    status: MeasurementStatusSchema,
});

export type Measurement = z.infer<typeof MeasurementSchema>;

export const LaunchModeSchema = z.enum(["iframe", "tab", "ask"]);

export type LaunchMode = z.infer<typeof LaunchModeSchema>;

export const AppSchema = z.object({
    clientId: z.string(),
    name: z.string(),
    description: z.string().nullish(),
    icon: z.string(),
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

export const SessionSchema = z.object({
    idp: z.enum(["helseid", "local-stub"]),
    claims: z.record(z.string(), z.string()),
    issuedAt: z.string().nullish(),
    expiresAt: z.string().nullish(),
});

export type Session = z.infer<typeof SessionSchema>;

export const SmartConfigurationSchema = z.object({
    issuer: z.string(),
    jwks_uri: z.string(),
    authorization_endpoint: z.string(),
    token_endpoint: z.string(),
    token_endpoint_auth_methods_supported: z.array(z.string()),
    capabilities: z.array(z.string()),
});

export type SmartConfiguration = z.infer<typeof SmartConfigurationSchema>;

export const CapabilityStatementSchema = z.object({
    fhirVersion: z.string(),
    rest: z
        .array(
            z.object({
                resource: z
                    .array(
                        z.object({
                            type: z.string(),
                            interaction: z.array(z.object({ code: z.string() })).nullish(),
                        }),
                    )
                    .nullish(),
            }),
        )
        .nullish(),
});

export type CapabilityStatement = z.infer<typeof CapabilityStatementSchema>;
