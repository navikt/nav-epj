/**
 * A simple mock endpoint that points to the resources in mock-oauth2-server when running with docker compose
 */
export function GET(): Response {
    return Response.json(
        {
            resourceType: 'CapabilityStatement',
            status: 'active',
            date: '2025-01-09T15:05:22+01:00',
            publisher: 'NAV IT',
            kind: 'instance',
            software: { name: 'NAV FHIR server', version: '1' },
            fhirVersion: '4.0.1',
            format: ['application/fhir+json', 'application/json'],
            rest: [
                {
                    mode: 'server',
                    security: {
                        extension: [
                            {
                                url: 'http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris',
                                extension: [
                                    { url: 'authorize', valueUri: 'http://localhost:8888/oauth2/authorize' },
                                    { url: 'token', valueUri: 'http://localhost:8888/oauth2/token' },
                                    { url: 'introspect', valueUri: 'http://localhost:8888/oauth2/introspect' },
                                ],
                            },
                        ],
                        cors: true,
                        service: [
                            {
                                coding: [
                                    {
                                        system: 'http://hl7.org/fhir/restful-security-service',
                                        code: 'SMART-on-FHIR',
                                        display: 'SMART-on-FHIR',
                                    },
                                ],
                            },
                        ],
                        description:
                            'Authentication via OAuth2 using SMART on FHIR framework (see http://docs.smarthealthit.org)',
                    },
                    resource: [
                        {
                            type: 'Patient',
                            profile: 'http://hl7.no/fhir/StructureDefinition/no-basis-Patient',
                            interaction: [
                                { code: 'read' },
                                { code: 'search-type' },
                                { code: 'create' },
                                { code: 'update' },
                                { code: 'delete' },
                            ],
                        },
                        {
                            type: 'Practitioner',
                            profile: 'http://hl7.no/fhir/StructureDefinition/no-basis-Practitioner',
                            interaction: [{ code: 'read' }, { code: 'search-type' }],
                        },
                        {
                            type: 'Encounter',
                            profile: 'http://hl7.no/fhir/StructureDefinition/no-basis-Encounter',
                            interaction: [
                                { code: 'read' },
                                { code: 'search-type' },
                                { code: 'create' },
                                { code: 'update' },
                                { code: 'delete' },
                            ],
                        },
                    ],
                    operation: [{ name: 'metadata', definition: 'CapabilityStatement' }],
                },
            ],
        },
        {
            headers: {
                'Access-Control-Allow-Origin': '*',
            },
        },
    )
}
