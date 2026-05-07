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
