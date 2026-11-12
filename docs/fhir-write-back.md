# FHIR write-back

## Documents and structured responses

The write-back model follows
[syk-inn ADR01](https://github.com/navikt/syk-inn/blob/main/docs/adr/ADR01%20-%20FHIR%20resources%20for%20writing%20data%20back%20to%20EHR.md):
a standalone DocumentReference carries the PDF, and `context.related` can refer
to a standalone QuestionnaireResponse. The two resource types may use the same
UUID. Neither resource is embedded in the other.

| Interaction | DocumentReference | QuestionnaireResponse |
|---|---|---|
| Read | `GET /fhir/DocumentReference/{id}` | `GET /fhir/QuestionnaireResponse/{id}` |
| Search | `GET /fhir/DocumentReference?patient={id}&encounter={id}` | `GET /fhir/QuestionnaireResponse?patient={id}&encounter={id}` |
| Create with server-assigned ID | `POST /fhir/DocumentReference` | `POST /fhir/QuestionnaireResponse` |
| Create or replace with client-assigned ID | `PUT /fhir/DocumentReference/{id}` | `PUT /fhir/QuestionnaireResponse/{id}` |
| Delete | `DELETE /fhir/DocumentReference/{id}` | `DELETE /fhir/QuestionnaireResponse/{id}` |

Search requires a patient (`patient` or `subject`); `encounter` is optional.
DocumentReference also supports the `related=QuestionnaireResponse/{id}` filter
alongside the patient criterion. PUT requires the body ID to match the URL.
New resources return `201 Created` with a `Location`; replacements return `200 OK`.
Create and update require their respective SMART `c` and `u` permissions.
Delete requires `d`; read and search require `r` and `s`. A client registration may
allow more scopes than an app requests, but the token carries only the requested ones.

Both resource types require a subject of `Patient/{uuid}` and a consultation
reference of `Encounter/{uuid}` belonging to that patient at the token's
organisation. System tokens with the relevant system scopes are not restricted
to one organisation. Existing resource IDs cannot be moved to another patient
or consultation, and FHIR writes cannot replace the clinician's own note.

DocumentReference supports `status=current`, one `type.coding` using
`urn:oid:2.16.578.1.12.4.1.1.9602|J01-2`, an optional `type.text` and description,
and one `content.attachment` with `contentType=application/pdf`. The attachment
can include a title, language and base64 PDF data. The context carries one
encounter, an optional period and at most one related
`QuestionnaireResponse/{uuid}` reference. Supplied authors must belong to the
consultation. Unsupported fields are rejected, except client `meta`, which is ignored.
Reads and searches return PDF data inline while it is available. Notes without a PDF
omit `attachment.data`.

QuestionnaireResponse retains its structured items and answers as FHIR JSON.
The `questionnaire` field identifies the form definition; it does not embed it.
A Practitioner author reference is checked against the consultation; other
FHIR author references are retained without resolving them.
A failed or missing QuestionnaireResponse does not prevent saving its
DocumentReference.

### Independent batch writes

`POST /fhir` (also `/fhir/`) accepts a Bundle with `type=batch` for DocumentReference and
QuestionnaireResponse GET, POST, PUT and DELETE entries. Each entry uses a
relative resource URL and undergoes the same scope, patient and organisation
checks as a direct request. The response is a `batch-response` Bundle with a
status for each entry. Failed entries also carry an OperationOutcome.

Entries succeed or fail independently. In particular, an invalid
QuestionnaireResponse must not roll back a valid PDF write. Transaction Bundles
are not supported.

### Limits, retention and deletion

PDFs are limited to **2,000,000 decoded bytes (2 MB)**. Direct writes and batch
requests have a **3,500,000-byte** HTTP body limit, enforced before JSON and
base64 decoding, including requests without a Content-Length. Stored
QuestionnaireResponse JSON is limited to **1,000,000 bytes**, and a batch accepts
at most **10 entries**. Oversized bodies and resources return `413`. Titles and
`type.text` accept up to 255 characters without control characters.
A `%PDF-` signature check rejects obvious non-PDF content; it is not complete
PDF validation or malware scanning.

Only PDF bytes expire after **14 days**. Attachment metadata, the
DocumentReference note and any QuestionnaireResponse remain. Reads and searches
omit expired `attachment.data`; the journal shows the expired state instead of
a download link. The download endpoint returns `404` for expired PDFs.
Subsequent PDF writes clear expired bytes in the same database transaction.
A PUT can send an expired attachment back as read, with `title` and `language` but no
`data`. This keeps the expired attachment as is and does not renew the retention period.
The fields must match the stored attachment, and the same shape is rejected with `400`
for a live attachment or a new document. An attachment with only `contentType` removes
the stored attachment.
There is no scheduled cleanup while the application is idle.

The journal's delete action removes the selected app document, its attachment
and its linked QuestionnaireResponse at the same patient and consultation.
It requires confirmation and leaves the clinician's note alone. FHIR DELETE
removes only the requested resource; it does not cascade to its related resource.

Attachments use the existing Postgres database, not an object-storage service.
Separating bytes from journal metadata avoids loading PDFs when opening the
journal. Expired bytes free space for Postgres to reuse, but they do not shrink the
provisioned disk or its fixed cost, so the 14-day window is not a hard storage
quota.

## Measurements

`POST /fhir/Observation` requires a create scope such as `patient/Observation.c`.
The supported request shape has:

- Exactly one LOINC coding with code and display.
- A `valueQuantity` with UCUM system, code, unit and value (at most 8 integer
  and 4 decimal digits).
- An `effectiveDateTime` in UTC with a `Z` suffix and a `status`.
- A `subject` of `Patient/{uuid}` and an `encounter` of `Encounter/{uuid}` at the
  token's organisation, belonging to that patient.

Components, notes and interpretations are rejected. The response is `201 Created`
with a server-generated ID and `Location`. A repeated POST creates a second
record. The API does not require the encounter to be ongoing.

Measurements appear in the journal's measurements tab; app-written documents
appear under «Dokumenter fra apper», separate from the clinician's own note.

## Registering clients

SMART App Launch 2.2 recommends confidential clients when an app can protect a
secret, and `private_key_jwt` over shared secrets when feasible.

A client registration needs:

- The `client_id`, auth method and required scopes.
- For `private_key_jwt`: a public JWK Set (inline) or a `jwksUri`. The client
  keeps the private key.
- For `client_secret_basic`: a secret in the Nais secret `nav-epj-client-config`,
  referenced from `application.yaml` as `${...}`. Never put secrets in chat or Git.
- Redirect and launch URIs, except for Backend Services (`client_credentials`).

Add the entry to `smart.clients` in `application.yaml` and deploy. Startup
validation rejects invalid registrations. The registration shape is described in
the [SMART client authentication guide](./smart-client-authentication-guide.md).

## Related documentation

- [SMART client authentication](./smart-client-authentication-guide.md):
  token endpoint authentication and registry configuration.
- [Frontend testing](../frontend/README.md#testing): real-backend browser
  acceptance for write-back.
- [Notes](./notes.md): local and environment OAuth configuration.
