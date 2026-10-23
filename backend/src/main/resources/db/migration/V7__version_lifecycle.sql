ALTER TABLE prd_document ADD COLUMN version_no_counter integer NOT NULL DEFAULT 0;
ALTER TABLE prd_document ADD COLUMN patch_checkpoint_revision bigint NOT NULL DEFAULT 0;

UPDATE prd_document p SET version_no_counter = (
    SELECT COALESCE(MAX(v.version_no), 0) FROM prd_version v WHERE v.prd_id = p.id
);
