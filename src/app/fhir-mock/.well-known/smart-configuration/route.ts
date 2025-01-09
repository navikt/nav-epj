/**
 * A simple mock endpoint that points to the resources in mock-oauth2-server when running with docker compose
 */
export function GET(): Response {
    return Response.json(
        {
            issuer: 'http://localhost:8888',
            jwks_uri: 'http://localhost:8888/oauth2/jwks',
            authorization_endpoint: 'http://localhost:8888/oauth2/authorize',
            token_endpoint: 'http://localhost:8888/oauth2/token',
            management_endpoint: 'http://localhost:8888/oauth2/manage',
            introspection_endpoint: 'http://localhost:8888/oauth2/introspect',
            revocation_endpoint: 'http://localhost:8888/oauth2/revoke',
            user_access_brand_bundle: '',
            user_access_brand_identifier: '',
            grant_types_supported: ['authorization_code', 'client_credentials', 'refresh_token'],
            scopes_supported: [
                'openid',
                'profile',
                'fhirUser',
                'launch',
                'patient/*.cruds',
                'patient/*.*',
                'encounter/*.cruds',
                'encounter/*.*',
                'user/*.cruds',
                'user/*.*',
                'offline_access',
            ],
            response_types_supported: ['code', 'token'],
            capabilities: [
                'launch-ehr',
                'launch-standalone',
                'client-public',
                'client-confidential-symmetric',
                'client-confidential-asymmetric',
                'sso-openid-connect',
                'context-passthrough-banner',
                'context-passthrough-style',
                'context-ehr-patient',
                'context-ehr-encounter',
                'context-standalone-patient',
                'context-standalone-encounter',
                'permission-offline',
                'permission-patient',
                'permission-user',
                'permission-v1',
                'permission-v2',
                'authorize-post',
            ],
            code_challenge_methods_supported: ['RS256'],
        },
        {
            headers: {
                'Access-Control-Allow-Origin': '*',
            },
        },
    )
}
