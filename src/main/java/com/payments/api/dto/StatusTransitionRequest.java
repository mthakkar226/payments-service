package com.payments.api.dto;

import com.payments.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public class StatusTransitionRequest {

    @NotNull
    private PaymentStatus status;

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}
