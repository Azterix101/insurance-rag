CREATE TABLE IF NOT EXISTS compliance_queue (
    id SERIAL PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    raw_s3_uri VARCHAR(512) NOT NULL,
    redacted_content TEXT,
    flagged_entities TEXT,
    status VARCHAR(50) DEFAULT 'PENDING_REVIEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS policy_chunks (
    id UUID PRIMARY KEY,
    parent_id UUID,
    chunk_content TEXT NOT NULL,
    embedding VECTOR(768),
    state_jurisdiction VARCHAR(50),
    policy_type VARCHAR(100),
    effective_year VARCHAR(10),
    document_section VARCHAR(100),
    related_chunk_ids TEXT[]
);