-- PrismPRD V1 全量 Schema
-- 约定：主键 UUID；时间 timestamptz；结构化内容 jsonb；草稿用 revision 乐观锁。

-- ============ 用户与权限 ============

CREATE TABLE app_user (
    id           uuid PRIMARY KEY,
    auth_source  varchar(16)  NOT NULL,                    -- OIDC / DEV
    issuer       varchar(255),                             -- OIDC Issuer；DEV 为空
    subject      varchar(255) NOT NULL,                    -- 外部身份 Subject；DEV 使用配置稳定标识
    username     varchar(128) NOT NULL,
    display_name varchar(128) NOT NULL,
    email        varchar(255),
    enabled      boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_external UNIQUE NULLS NOT DISTINCT (issuer, subject)
);

CREATE INDEX idx_app_user_subject ON app_user (auth_source, subject);

CREATE TABLE prd_document (
    id                 uuid PRIMARY KEY,
    title              varchar(255) NOT NULL,
    status             varchar(32)  NOT NULL DEFAULT 'DRAFT', -- DRAFT / REVIEWING / NEED_CHANGES / APPROVED / ARCHIVED
    owner_id           uuid         NOT NULL REFERENCES app_user (id),
    draft_json         jsonb        NOT NULL,
    draft_markdown     text         NOT NULL DEFAULT '',
    revision           bigint       NOT NULL DEFAULT 0,      -- 草稿乐观锁
    current_version_id uuid,                                  -- 当前生效版本
    current_score      numeric(3,1),                          -- 最近成功 Review 综合分
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_prd_document_owner ON prd_document (owner_id);
CREATE INDEX idx_prd_document_updated ON prd_document (updated_at DESC);

CREATE TABLE prd_member (
    id         uuid PRIMARY KEY,
    prd_id     uuid        NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES app_user (id),
    role       varchar(16) NOT NULL,                          -- EDITOR / REVIEWER / VIEWER
    created_by uuid        NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_prd_member UNIQUE (prd_id, user_id)
);

CREATE INDEX idx_prd_member_user ON prd_member (user_id);

-- ============ PRD 版本与附件 ============

CREATE TABLE prd_version (
    id                uuid PRIMARY KEY,
    prd_id            uuid        NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    version_no        int         NOT NULL,
    editor_json       jsonb       NOT NULL,                   -- 不可变 TipTap JSON Snapshot
    markdown_snapshot text        NOT NULL,                   -- AI / Diff / 导出用投影
    source            varchar(16) NOT NULL,                   -- HUMAN / AI / IMPORT
    change_summary    text        NOT NULL DEFAULT '',
    created_by        uuid        NOT NULL REFERENCES app_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_prd_version_no UNIQUE (prd_id, version_no)
);

ALTER TABLE prd_document
    ADD CONSTRAINT fk_prd_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES prd_version (id);

CREATE TABLE prd_attachment (
    id                uuid PRIMARY KEY,
    prd_id            uuid         NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    file_name         varchar(512) NOT NULL,
    storage_key       varchar(512) NOT NULL,
    mime_type         varchar(128) NOT NULL,
    size_bytes        bigint       NOT NULL DEFAULT 0,
    processing_status varchar(16)  NOT NULL DEFAULT 'PROCESSING', -- PROCESSING / READY / FAILED
    extracted_text    text,
    created_by        uuid         NOT NULL REFERENCES app_user (id),
    created_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_prd_attachment_prd ON prd_attachment (prd_id);

-- ============ AI 会话 ============

CREATE TABLE chat_session (
    id         uuid PRIMARY KEY,
    prd_id     uuid         NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    title      varchar(255) NOT NULL DEFAULT 'AI 助手',
    created_by uuid         NOT NULL REFERENCES app_user (id),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_session_prd ON chat_session (prd_id, updated_at DESC);

CREATE TABLE chat_message (
    id           uuid PRIMARY KEY,
    session_id   uuid        NOT NULL REFERENCES chat_session (id) ON DELETE CASCADE,
    role         varchar(16) NOT NULL,                        -- USER / ASSISTANT / TOOL
    content      text        NOT NULL DEFAULT '',
    tool_name    varchar(64),
    tool_payload jsonb,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session ON chat_message (session_id, created_at DESC);

-- ============ Review：Rubric / Job / Report / Score / Finding ============

CREATE TABLE review_rubric (
    id          uuid PRIMARY KEY,
    rubric_key  varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    version     int          NOT NULL,
    description text         NOT NULL DEFAULT '',
    enabled     boolean      NOT NULL DEFAULT true,
    created_by  uuid         NOT NULL REFERENCES app_user (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_rubric UNIQUE (rubric_key, version)
);

CREATE TABLE review_dimension (
    id                  uuid PRIMARY KEY,
    rubric_id           uuid        NOT NULL REFERENCES review_rubric (id) ON DELETE CASCADE,
    dimension_key       varchar(64) NOT NULL,
    name                varchar(128) NOT NULL,
    description         text        NOT NULL DEFAULT '',
    checklist           jsonb       NOT NULL DEFAULT '[]',    -- 检查项列表
    score_anchor        jsonb       NOT NULL DEFAULT '{}',    -- 分数锚点
    severity_anchor     jsonb       NOT NULL DEFAULT '{}',    -- 严重级别锚点
    applicability_rule  text,                                 -- 适用性判定说明
    display_order       int         NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_dimension UNIQUE (rubric_id, dimension_key)
);

CREATE TABLE review_job (
    id              uuid PRIMARY KEY,
    prd_id          uuid        NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    prd_version_id  uuid        NOT NULL REFERENCES prd_version (id),
    rubric_id       uuid        NOT NULL REFERENCES review_rubric (id),
    status          varchar(16) NOT NULL DEFAULT 'PENDING',   -- PENDING / RUNNING / SUCCESS / FAILED
    model_profile   varchar(64) NOT NULL,
    error_message   text,
    created_by      uuid        NOT NULL REFERENCES app_user (id),
    started_at      timestamptz,
    finished_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_job_prd ON review_job (prd_id, created_at DESC);
CREATE INDEX idx_review_job_version ON review_job (prd_version_id);

CREATE TABLE review_report (
    id             uuid PRIMARY KEY,
    review_job_id  uuid         NOT NULL UNIQUE REFERENCES review_job (id) ON DELETE CASCADE,
    overall_score  numeric(3,1),
    readiness      varchar(32)  NOT NULL,                     -- BLOCKED / READY / CONDITIONALLY_READY / NEEDS_IMPROVEMENT
    summary        text         NOT NULL DEFAULT '',
    strengths      jsonb        NOT NULL DEFAULT '[]',
    major_risks    jsonb        NOT NULL DEFAULT '[]',
    priorities     jsonb        NOT NULL DEFAULT '[]',
    model_name     varchar(64)  NOT NULL DEFAULT '',
    model_version  varchar(64)  NOT NULL DEFAULT '',
    prompt_version varchar(32)  NOT NULL DEFAULT '',
    rubric_version int          NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE review_score (
    id            uuid PRIMARY KEY,
    report_id     uuid        NOT NULL REFERENCES review_report (id) ON DELETE CASCADE,
    dimension_key varchar(64) NOT NULL,
    applicable    boolean     NOT NULL DEFAULT true,
    score         numeric(3,1),
    rationale     text        NOT NULL DEFAULT '',
    evidence      jsonb       NOT NULL DEFAULT '[]',
    suggestions   jsonb       NOT NULL DEFAULT '[]',
    display_order int         NOT NULL DEFAULT 0,
    CONSTRAINT uq_review_score UNIQUE (report_id, dimension_key)
);

CREATE TABLE review_finding (
    id          uuid PRIMARY KEY,
    report_id   uuid        NOT NULL REFERENCES review_report (id) ON DELETE CASCADE,
    title       varchar(512) NOT NULL,
    dimensions  jsonb       NOT NULL DEFAULT '[]',             -- 关联 dimension_key 列表
    severity    varchar(16) NOT NULL,                          -- CRITICAL / MAJOR / MINOR / SUGGESTION
    block_id    varchar(64),
    quote       text,
    evidence    jsonb       NOT NULL DEFAULT '[]',
    reason      text        NOT NULL DEFAULT '',
    suggestion  text        NOT NULL DEFAULT '',
    status      varchar(16) NOT NULL DEFAULT 'OPEN',           -- OPEN / RESOLVED / IGNORED
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_finding_report ON review_finding (report_id);
CREATE INDEX idx_review_finding_block ON review_finding (block_id);

-- ============ Patch / Comment / TestCase ============

CREATE TABLE prd_patch (
    id                   uuid PRIMARY KEY,
    prd_id               uuid        NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    source               varchar(16) NOT NULL,                 -- INLINE_AI / FINDING_FIX / SECTION
    source_finding_id    uuid REFERENCES review_finding (id) ON DELETE SET NULL,
    source_version_id    uuid REFERENCES prd_version (id) ON DELETE SET NULL,
    base_draft_revision  bigint      NOT NULL,
    scope                varchar(16) NOT NULL,                 -- INLINE / BLOCK
    operation            varchar(16) NOT NULL,                 -- REPLACE / INSERT_BEFORE / INSERT_AFTER / DELETE
    target_block_id      varchar(64),
    source_quote         text,
    new_content_markdown text,
    reason               text        NOT NULL DEFAULT '',
    status               varchar(16) NOT NULL DEFAULT 'PENDING', -- PENDING / APPLIED / REJECTED / STALE
    created_by           uuid        NOT NULL REFERENCES app_user (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    applied_at           timestamptz
);

CREATE INDEX idx_prd_patch_prd ON prd_patch (prd_id, created_at DESC);

CREATE TABLE prd_comment (
    id         uuid PRIMARY KEY,
    prd_id     uuid        NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    block_id   varchar(64) NOT NULL,
    quote      text,
    content    text        NOT NULL,
    author_id  uuid        NOT NULL REFERENCES app_user (id),
    status     varchar(16) NOT NULL DEFAULT 'OPEN',            -- OPEN / RESOLVED
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_prd_comment_prd ON prd_comment (prd_id, created_at DESC);

CREATE TABLE test_case (
    id              uuid PRIMARY KEY,
    prd_version_id  uuid        NOT NULL REFERENCES prd_version (id) ON DELETE CASCADE,
    scenario        varchar(512) NOT NULL,
    preconditions   text        NOT NULL DEFAULT '',
    steps           jsonb       NOT NULL DEFAULT '[]',
    input_data      text,
    expected_result text        NOT NULL DEFAULT '',
    case_type       varchar(32) NOT NULL DEFAULT 'FUNCTIONAL', -- FUNCTIONAL / BOUNDARY / EXCEPTION / SECURITY
    source_block_id varchar(64),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_test_case_version ON test_case (prd_version_id);

-- ============ Knowledge ============

CREATE TABLE knowledge_base (
    id          uuid PRIMARY KEY,
    name        varchar(255) NOT NULL,
    description text         NOT NULL DEFAULT '',
    enabled     boolean      NOT NULL DEFAULT true,
    created_by  uuid         NOT NULL REFERENCES app_user (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_document (
    id                uuid PRIMARY KEY,
    knowledge_base_id uuid         NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    title             varchar(512) NOT NULL,
    source_type       varchar(16)  NOT NULL DEFAULT 'UPLOAD', -- UPLOAD / URL
    storage_key       varchar(512),
    mime_type         varchar(128),
    parsed_text       text,
    processing_status varchar(16)  NOT NULL DEFAULT 'PROCESSING', -- PROCESSING / READY / FAILED
    error_message     text,
    created_by        uuid         NOT NULL REFERENCES app_user (id),
    created_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_document_kb ON knowledge_document (knowledge_base_id);

CREATE TABLE knowledge_chunk (
    id                   uuid PRIMARY KEY,
    knowledge_document_id uuid       NOT NULL REFERENCES knowledge_document (id) ON DELETE CASCADE,
    chunk_text           text        NOT NULL,
    section_path         varchar(512),
    chunk_index          int         NOT NULL,
    metadata             jsonb       NOT NULL DEFAULT '{}',
    -- pgvector 未随便携版 PostgreSQL 提供；知识库阶段引入 vector 扩展后迁移为 vector(1024)
    embedding            float8[],
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_knowledge_chunk UNIQUE (knowledge_document_id, chunk_index)
);

CREATE TABLE prd_knowledge_binding (
    id                uuid PRIMARY KEY,
    prd_id            uuid NOT NULL REFERENCES prd_document (id) ON DELETE CASCADE,
    knowledge_base_id uuid NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    created_by        uuid NOT NULL REFERENCES app_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_prd_knowledge_binding UNIQUE (prd_id, knowledge_base_id)
);
