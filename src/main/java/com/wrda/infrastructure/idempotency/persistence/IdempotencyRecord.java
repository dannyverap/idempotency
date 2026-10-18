package com.wrda.infrastructure.idempotency.persistence;

import com.wrda.application.idempotency.model.IdempotencyStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord extends PanacheEntityBase {

    @Id
    @Column(name = "idempotency_key", length = 100)
    public String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    public String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public IdempotencyStatus status;

    @Column(name = "status_code")
    public Integer statusCode;

    @Column(name = "response_body")
    public String responseBody;

    @Column(name = "content_type", length = 100)
    public String contentType;

    @Column(name = "location", length = 500)
    public String location;

    /** Epoch en milisegundos. */
    @Column(name = "created_at", nullable = false)
    public long createdAt;

    @Column(name = "updated_at", nullable = false)
    public long updatedAt;

    @Column(name = "expires_at", nullable = false)
    public long expiresAt;
}
