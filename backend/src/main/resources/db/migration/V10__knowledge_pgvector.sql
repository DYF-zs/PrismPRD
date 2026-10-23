CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge_chunk
    ALTER COLUMN embedding TYPE vector USING embedding::vector,
    ALTER COLUMN embedding SET NOT NULL;

ALTER TABLE knowledge_chunk
    ADD CONSTRAINT ck_knowledge_chunk_vector_nonzero CHECK (vector_norm(embedding) > 0);
