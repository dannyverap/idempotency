-- Tabla de idempotencia. Las fechas son epoch en milisegundos (BIGINT).
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key CHAR(64)     NOT NULL PRIMARY KEY,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    status_code     INTEGER,
    resource_reference VARCHAR(100),
    created_at      BIGINT       NOT NULL,
    updated_at      BIGINT       NOT NULL,
    expires_at      BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_idempotency_expires_at ON idempotency_keys (expires_at);

CREATE TABLE IF NOT EXISTS orders (
    order_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    amount      DECIMAL(19,4) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  BIGINT       NOT NULL
);
