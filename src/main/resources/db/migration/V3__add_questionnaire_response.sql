-- Add QuestionnaireResponse table for storing structured sick note data
-- QuestionnaireResponse is a standalone FHIR resource linked to DocumentReference via context.related

CREATE TABLE questionnaire_response (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);
