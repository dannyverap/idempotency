-- SQL Server schema for this demo. Run through reviewed versioned migrations in production.
IF OBJECT_ID(N'dbo.idempotency_keys', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.idempotency_keys (
        idempotency_key CHAR(64) NOT NULL CONSTRAINT pk_idempotency_keys PRIMARY KEY,
        request_hash CHAR(64) NOT NULL,
        status VARCHAR(20) NOT NULL,
        status_code INT NULL,
        encrypted_response NVARCHAR(MAX) NULL,
        created_at BIGINT NOT NULL,
        updated_at BIGINT NOT NULL,
        expires_at BIGINT NOT NULL,
        CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
    )
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_idempotency_status_expires' AND object_id = OBJECT_ID(N'dbo.idempotency_keys'))
    CREATE INDEX ix_idempotency_status_expires ON dbo.idempotency_keys (status, expires_at)
IF OBJECT_ID(N'dbo.orders', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.orders (
        order_id VARCHAR(36) NOT NULL CONSTRAINT pk_orders PRIMARY KEY,
        customer_id VARCHAR(100) NOT NULL,
        amount DECIMAL(19,4) NOT NULL,
        status VARCHAR(20) NOT NULL,
        created_at BIGINT NOT NULL
    )
END
IF OBJECT_ID(N'dbo.payments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.payments (
        payment_id VARCHAR(36) NOT NULL CONSTRAINT pk_payments PRIMARY KEY,
        order_id VARCHAR(36) NOT NULL,
        amount DECIMAL(19,4) NOT NULL,
        status VARCHAR(20) NOT NULL,
        created_at BIGINT NOT NULL,
        CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id)
    )
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_payments_order_id' AND object_id = OBJECT_ID(N'dbo.payments'))
    CREATE INDEX ix_payments_order_id ON dbo.payments (order_id)
