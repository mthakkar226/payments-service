package com.payments.api.dto;

import com.payments.domain.Payment;
import com.payments.domain.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PaymentResponse {

    private UUID id;
    private UUID accountId;
    private Long amount;
    private String currency;
    private PaymentStatus status;
    private Long version;
    private OffsetDateTime createdAt;

    public static PaymentResponse from(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.id = p.getId();
        r.accountId = p.getAccountId();
        r.amount = p.getAmount();
        r.currency = p.getCurrency();
        r.status = p.getStatus();
        r.version = p.getVersion();
        r.createdAt = p.getCreatedAt();
        return r;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
