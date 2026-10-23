ALTER TABLE prd_patch ADD COLUMN session_id uuid REFERENCES chat_session(id) ON DELETE SET NULL;
ALTER TABLE prd_patch ADD COLUMN applied_revision bigint;
ALTER TABLE prd_version ADD COLUMN draft_revision bigint;

UPDATE prd_patch SET applied_revision = base_draft_revision + 1 WHERE status = 'APPLIED';
UPDATE prd_patch p SET session_id = (
    SELECT m.session_id FROM chat_message m JOIN chat_session s ON s.id = m.session_id
    WHERE s.prd_id = p.prd_id AND m.role = 'TOOL' AND m.tool_name = 'generate_patch'
      AND m.content LIKE '%' || p.id::text || '%'
    ORDER BY m.created_at LIMIT 1
) WHERE p.source = 'AGENT_CHAT';

CREATE TABLE prd_version_patch (
    version_id uuid NOT NULL REFERENCES prd_version(id) ON DELETE CASCADE,
    patch_id uuid NOT NULL UNIQUE REFERENCES prd_patch(id) ON DELETE CASCADE,
    PRIMARY KEY (version_id, patch_id)
);
CREATE INDEX idx_prd_patch_session ON prd_patch(session_id, created_at);
