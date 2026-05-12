-- Repeatable migration: Seeds local development data
-- This file re-runs whenever its checksum changes

-- Only seed if we're in local environment (check for empty practitioners table)
-- Using INSERT ... ON CONFLICT to make this idempotent

INSERT INTO practitioner (id, data) VALUES
(
  'practitioner-001',
  '{
    "resourceType": "Practitioner",
    "id": "practitioner-001",
    "meta": {
      "profile": ["http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"]
    },
    "identifier": [{
      "system": "urn:oid:2.16.578.1.12.4.1.4.4",
      "value": "9144889"
    }],
    "active": true,
    "name": [{
      "family": "Boom",
      "given": ["Carl"],
      "prefix": ["Dr."]
    }],
    "gender": "male",
    "birthDate": "1975-06-20"
  }'::jsonb
),
(
  'practitioner-002',
  '{
    "resourceType": "Practitioner",
    "id": "practitioner-002",
    "meta": {
      "profile": ["http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"]
    },
    "identifier": [{
      "system": "urn:oid:2.16.578.1.12.4.1.4.4",
      "value": "9144890"
    }],
    "active": true,
    "name": [{
      "family": "Mudskipper",
      "given": ["Zev"],
      "prefix": ["Dr."]
    }],
    "gender": "female",
    "birthDate": "1982-09-14"
  }'::jsonb
),
(
  'practitioner-003',
  '{
    "resourceType": "Practitioner",
    "id": "practitioner-003",
    "meta": {
      "profile": ["http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner"]
    },
    "identifier": [{
      "system": "urn:oid:2.16.578.1.12.4.1.4.4",
      "value": "9144891"
    }],
    "active": false,
    "name": [{
      "family": "Andrews",
      "given": ["Chris"],
      "prefix": ["Dr."]
    }],
    "gender": "male",
    "birthDate": "1968-02-28"
  }'::jsonb
)
ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data;

INSERT INTO encounter (id, data) VALUES
(
  'encounter-001',
  '{
    "resourceType": "Encounter",
    "id": "encounter-001",
    "status": "finished",
    "class": {
      "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
      "code": "AMB"
    },
    "type": [{
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.8432",
        "code": "kontaktype"
      }]
    }],
    "subject": {
      "reference": "Patient/patient-001"
    },
    "participant": [{
      "individual": {
        "reference": "Practitioner/practitioner-001"
      }
    }],
    "diagnosis": [{
      "condition": {
        "reference": "Condition/condition-001"
      }
    }],
    "serviceProvider": {
      "reference": "Organization/organization-001"
    }
  }'::jsonb
)
ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data;

INSERT INTO condition (id, data) VALUES
(
  'condition-001',
  '{
    "resourceType": "Condition",
    "id": "condition-001",
    "subject": {
      "reference": "Patient/patient-001"
    },
    "code": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.7170",
        "code": "L73",
        "display": "Brudd legg/ankel"
      }]
    }
  }'::jsonb
),
(
  'condition-002',
  '{
    "resourceType": "Condition",
    "id": "condition-002",
    "subject": {
      "reference": "Patient/patient-002"
    },
    "code": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.7170",
        "code": "P74",
        "display": "Angstlidelse"
      }]
    }
  }'::jsonb
),
(
  'condition-003',
  '{
    "resourceType": "Condition",
    "id": "condition-003",
    "subject": {
      "reference": "Patient/patient-003"
    },
    "code": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.7110",
        "code": "A051",
        "display": "Botulisme"
      }]
    }
  }'::jsonb
)
ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data;

INSERT INTO organization (id, data) VALUES
(
  'organization-001',
  '{
    "resourceType": "Organization",
    "id": "organization-001",
    "meta": {
      "profile": ["http://hl7.no/fhir/StructureDefinition/no-basis-Organization"]
    },
    "identifier": [
      {
        "system": "urn:oid:2.16.578.1.12.4.1.4.101",
        "value": "organisasjonsnummer / ENH"
      },
      {
        "system": "urn:oid:2.16.578.1.12.4.1.2",
        "value": "her-id"
      }
    ],
    "telecom": [{
      "system": "phone",
      "value": "+47 12345678"
    }]
  }'::jsonb
)
ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data;

INSERT INTO document_reference (id, data) VALUES
(
  'documentreference-001',
  '{
    "resourceType": "DocumentReference",
    "id": "documentreference-001",
    "status": "current",
    "type": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.9602",
        "code": "J01-2",
        "display": "Sykmeldinger og trygdesaker"
      }]
    },
    "description": "100% Sykmelding fra 01.06.2024 til 07.06.2024",
    "subject": {
      "reference": "Patient/patient-001"
    },
    "author": [{
      "reference": "Practitioner/practitioner-001"
    }],
    "content": [{
      "attachment": {
        "title": "Sykmelding.pdf",
        "language": "no-NO",
        "contentType": "application/pdf",
        "data": "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
      }
    }],
    "context": {
      "encounter": [{
        "reference": "Encounter/encounter-001"
      }]
    }
  }'::jsonb
),
(
  'documentreference-002',
  '{
    "resourceType": "DocumentReference",
    "id": "documentreference-002",
    "status": "current",
    "type": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.9602",
        "code": "J01-2",
        "display": "Sykmeldinger og trygdesaker"
      }]
    },
    "description": "50% Sykmelding fra 10.03.2024 til 24.03.2024",
    "subject": {
      "reference": "Patient/patient-002"
    },
    "author": [{
      "reference": "Practitioner/practitioner-002"
    }],
    "content": [{
      "attachment": {
        "title": "Sykmelding.pdf",
        "language": "no-NO",
        "contentType": "application/pdf",
        "data": "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
      }
    }],
    "context": {
      "encounter": [{
        "reference": "Encounter/encounter-002"
      }]
    }
  }'::jsonb
),
(
  'documentreference-003',
  '{
    "resourceType": "DocumentReference",
    "id": "documentreference-003",
    "status": "current",
    "type": {
      "coding": [{
        "system": "urn:oid:2.16.578.1.12.4.1.1.9602",
        "code": "J01-2",
        "display": "Sykmeldinger og trygdesaker"
      }]
    },
    "description": "100% Sykmelding fra 20.04.2024 til 30.04.2024",
    "subject": {
      "reference": "Patient/patient-003"
    },
    "author": [{
      "reference": "Practitioner/practitioner-001"
    }],
    "content": [{
      "attachment": {
        "title": "Sykmelding.pdf",
        "language": "no-NO",
        "contentType": "application/pdf",
        "data": "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PgplbmRvYmoK"
      }
    }],
    "context": {
      "encounter": [{
        "reference": "Encounter/encounter-003"
      }]
    }
  }'::jsonb
)
ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data;
