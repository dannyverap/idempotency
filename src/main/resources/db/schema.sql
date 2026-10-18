-- Tabla de idempotencia. Las fechas son epoch en milisegundos (BIGINT).
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(100) NOT NULL PRIMARY KEY,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    status_code     INTEGER,
    response_body   TEXT,
    content_type    VARCHAR(100),
    location        VARCHAR(500),
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    expires_at      BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_idempotency_expires_at ON idempotency_keys (expires_at);
