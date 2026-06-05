package com.payments.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    SETTLED,
    FAILED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PENDING,     Set.of(AUTHORIZED, FAILED));
        ALLOWED_TRANSITIONS.put(AUTHORIZED,  Set.of(CAPTURED,   FAILED));
        ALLOWED_TRANSITIONS.put(CAPTURED,    Set.of(SETTLED,    FAILED));
        ALLOWED_TRANSITIONS.put(SETTLED,     Set.of());
        ALLOWED_TRANSITIONS.put(FAILED,      Set.of());
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void assertCanTransitionTo(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Transition from " + this + " to " + target + " is not allowed");
        }
    }
}
