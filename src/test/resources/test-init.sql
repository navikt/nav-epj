-- Patient table
CREATE TABLE patient (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);

-- Practitioner table
CREATE TABLE practitioner (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);

-- Organization table
CREATE TABLE organization (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);

-- Encounter table
CREATE TABLE encounter (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);

-- Condition table
CREATE TABLE condition (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);

-- Document Reference table
CREATE TABLE document_reference (
    id TEXT NOT NULL PRIMARY KEY,
    data JSONB NOT NULL
);
