CREATE TABLE ai_model_configuration (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_user(id),
    name varchar(80) NOT NULL,
    provider varchar(24) NOT NULL CHECK (provider IN ('deepseek', 'zhipuai', 'openai')),
    model varchar(160) NOT NULL,
    base_url varchar(1024) NOT NULL,
    api_key_cipher text NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (owner_id, name),
    UNIQUE (id, owner_id)
);

CREATE TABLE ai_model_selection (
    owner_id uuid PRIMARY KEY REFERENCES app_user(id),
    configuration_id uuid,
    FOREIGN KEY (configuration_id, owner_id)
        REFERENCES ai_model_configuration(id, owner_id)
);

CREATE INDEX idx_ai_model_owner ON ai_model_configuration(owner_id);

