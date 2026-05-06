-- Patient table
CREATE TABLE patient (
    id TEXT NOT NULL,
    meta JSONB NOT NULL,
    identifier JSONB NOT NULL,
    active BOOLEAN NOT NULL,
    name JSONB NOT NULL,
    gender JSONB NOT NULL,
    birth_date JSONB NOT NULL
);

-- Practitioner table
CREATE TABLE practitioner (
    id TEXT NOT NULL,
    meta JSONB NOT NULL,
    identifier JSONB NOT NULL,
    active BOOLEAN NOT NULL,
    name JSONB NOT NULL,
    gender JSONB NOT NULL,
    birth_date JSONB NOT NULL
);

-- Organization table
CREATE TABLE organization (
    id TEXT NOT NULL,
    meta JSONB NOT NULL,
    identifier JSONB NOT NULL,
    telecom JSONB NOT NULL
);

-- Encounter table
CREATE TABLE encounter (
    id TEXT NOT NULL,
    subject JSONB NOT NULL,
    participant JSONB NOT NULL,
    diagnosis JSONB NOT NULL,
    "serviceProvider" JSONB NOT NULL,
    status JSONB NOT NULL,
    type JSONB NOT NULL,
    class JSONB NOT NULL
);

-- Condition table
CREATE TABLE condition (
    id TEXT NOT NULL,
    subject JSONB NOT NULL,
    type JSONB NOT NULL
);

-- Document Reference table
CREATE TABLE document_reference (
    id TEXT NOT NULL,
    status JSONB NOT NULL,
    type JSONB NOT NULL,
    description JSONB NOT NULL,
    subject JSONB NOT NULL,
    author JSONB NOT NULL,
    content JSONB NOT NULL,
    context JSONB NOT NULL
);
