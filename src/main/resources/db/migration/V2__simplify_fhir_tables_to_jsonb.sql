-- Migrate FHIR tables from individual columns to single JSONB data column.
-- This allows storing full FHIR resources using FhirR4Json serialization.

-- Patient table
ALTER TABLE patient ADD COLUMN data JSONB;
ALTER TABLE patient DROP COLUMN meta, DROP COLUMN identifier, DROP COLUMN active, DROP COLUMN name, DROP COLUMN gender, DROP COLUMN birth_date;
ALTER TABLE patient ALTER COLUMN data SET NOT NULL;
ALTER TABLE patient ADD PRIMARY KEY (id);

-- Practitioner table
ALTER TABLE practitioner ADD COLUMN data JSONB;
ALTER TABLE practitioner DROP COLUMN meta, DROP COLUMN identifier, DROP COLUMN active, DROP COLUMN name, DROP COLUMN gender, DROP COLUMN birth_date;
ALTER TABLE practitioner ALTER COLUMN data SET NOT NULL;
ALTER TABLE practitioner ADD PRIMARY KEY (id);

-- Organization table
ALTER TABLE organization ADD COLUMN data JSONB;
ALTER TABLE organization DROP COLUMN meta, DROP COLUMN identifier, DROP COLUMN telecom;
ALTER TABLE organization ALTER COLUMN data SET NOT NULL;
ALTER TABLE organization ADD PRIMARY KEY (id);

-- Encounter table
ALTER TABLE encounter ADD COLUMN data JSONB;
ALTER TABLE encounter DROP COLUMN subject, DROP COLUMN participant, DROP COLUMN diagnosis, DROP COLUMN "serviceProvider", DROP COLUMN status, DROP COLUMN type, DROP COLUMN class;
ALTER TABLE encounter ALTER COLUMN data SET NOT NULL;
ALTER TABLE encounter ADD PRIMARY KEY (id);

-- Condition table
ALTER TABLE condition ADD COLUMN data JSONB;
ALTER TABLE condition DROP COLUMN subject, DROP COLUMN type;
ALTER TABLE condition ALTER COLUMN data SET NOT NULL;
ALTER TABLE condition ADD PRIMARY KEY (id);

-- Document Reference table
ALTER TABLE document_reference ADD COLUMN data JSONB;
ALTER TABLE document_reference DROP COLUMN status, DROP COLUMN type, DROP COLUMN description, DROP COLUMN subject, DROP COLUMN author, DROP COLUMN content, DROP COLUMN context;
ALTER TABLE document_reference ALTER COLUMN data SET NOT NULL;
ALTER TABLE document_reference ADD PRIMARY KEY (id);
