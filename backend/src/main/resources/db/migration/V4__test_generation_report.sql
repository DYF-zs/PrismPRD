CREATE TABLE test_generation_report (
    prd_version_id uuid PRIMARY KEY REFERENCES prd_version(id) ON DELETE CASCADE,
    result jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE test_acceptance_patch (
    generation_id uuid NOT NULL,
    issue_index integer NOT NULL,
    patch_id uuid NOT NULL REFERENCES prd_patch(id) ON DELETE CASCADE,
    PRIMARY KEY (generation_id, issue_index)
);
