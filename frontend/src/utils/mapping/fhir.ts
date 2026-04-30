import * as z from "zod";

export const PatientSchema = z.object({
  id: z.string(),
  name: z.array(
    z.object({
      given: z.array(z.string()),
      family: z.string(),
    }),
  ),
  birthDate: z.string(),
});

export type FhirPatient = z.infer<typeof PatientSchema>;

export const PractitionerSchema = z.object({
  id: z.string(),
  name: z.array(
    z.object({
      given: z.array(z.string()),
      family: z.string(),
      title: z.string().optional(),
    }),
  ),
  birthDate: z.string(),
});

export type FhirPractitioner = z.infer<typeof PractitionerSchema>;

export const ConditionSchema = z.object({
  id: z.string(),
  code: z.object({
    coding: z.array(
      z.object({
        system: z.string(),
        code: z.string(),
        display: z.string().optional(),
      }),
    ),
  }),
  subject: z.object({
    reference: z.string(),
  }),
});

export type FhirCondition = z.infer<typeof ConditionSchema>;

export const EncounterSchema = z.object({
  id: z.string(),
  status: z.string(),
  subject: z.object({
    reference: z.string(),
  }),
  participant: z.array(
    z.object({
      individual: z.object({
        reference: z.string(),
      }),
    }),
  ),
  diagnosis: z.array(
    z.object({
      condition: z.object({
        reference: z.string(),
      }),
    }),
  ),
  serviceProvider: z.object({
    reference: z.string(),
  }),
});

export type FhirEncounter = z.infer<typeof EncounterSchema>;

export const OrganizationSchema = z.object({
  id: z.string(),
  identifier: z.array(
    z.object({
      system: z.string(),
      value: z.string(),
    }),
  ),
  telecom: z.array(
    z.object({
      system: z.string(),
      value: z.string(),
    }),
  ),
});

export type FhirOrganization = z.infer<typeof OrganizationSchema>;
