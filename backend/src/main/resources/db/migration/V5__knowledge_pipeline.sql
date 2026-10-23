ALTER TABLE knowledge_document ADD COLUMN file_size bigint NOT NULL DEFAULT 0;
ALTER TABLE knowledge_document ADD COLUMN embedding_model varchar(255);
ALTER TABLE knowledge_document ADD COLUMN embedding_dimensions integer;
ALTER TABLE knowledge_document ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX idx_knowledge_binding_kb ON prd_knowledge_binding(knowledge_base_id);
CREATE INDEX idx_knowledge_doc_status ON knowledge_document(processing_status);

-- Exact cosine search uses the existing float8[] column for the portable local database.
-- Model and dimension are stored with each document to prevent mixing vector spaces.
