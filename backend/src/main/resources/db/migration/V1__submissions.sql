CREATE TABLE submissions (
    id           UUID PRIMARY KEY,
    client_id    VARCHAR(64)  NOT NULL,
    problem_slug VARCHAR(100) NOT NULL,
    code         TEXT         NOT NULL,
    samples_only BOOLEAN      NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    verdict      VARCHAR(32),
    passed       INT          NOT NULL DEFAULT 0,
    total        INT          NOT NULL DEFAULT 0,
    result       JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ
);

CREATE INDEX idx_submissions_history ON submissions (client_id, problem_slug, created_at DESC);
CREATE INDEX idx_submissions_unfinished ON submissions (status, created_at) WHERE status <> 'DONE';
