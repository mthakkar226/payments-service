package com.payments.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class PaymentStatusTest {

    // --- Valid transitions ---

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "PENDING,    AUTHORIZED",
        "PENDING,    FAILED",
        "AUTHORIZED, CAPTURED",
        "AUTHORIZED, FAILED",
        "CAPTURED,   SETTLED",
        "CAPTURED,   FAILED",
    })
    void validTransitions_allowed(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "PENDING,    CAPTURED",
        "PENDING,    SETTLED",
        "AUTHORIZED, PENDING",
        "AUTHORIZED, SETTLED",
        "CAPTURED,   PENDING",
        "CAPTURED,   AUTHORIZED",
        "SETTLED,    CAPTURED",
        "SETTLED,    FAILED",
        "FAILED,     PENDING",
        "FAILED,     AUTHORIZED",
    })
    void invalidTransitions_rejected(PaymentStatus from, PaymentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void assertCanTransitionTo_throwsOnInvalidTransition() {
        assertThatThrownBy(() -> PaymentStatus.SETTLED.assertCanTransitionTo(PaymentStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SETTLED")
                .hasMessageContaining("PENDING");
    }

    @Test
    void assertCanTransitionTo_doesNotThrowOnValidTransition() {
        assertThatCode(() -> PaymentStatus.PENDING.assertCanTransitionTo(PaymentStatus.AUTHORIZED))
                .doesNotThrowAnyException();
    }

    @Test
    void terminalStates_haveNoAllowedTransitions() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.SETTLED.canTransitionTo(target)).isFalse();
            assertThat(PaymentStatus.FAILED.canTransitionTo(target)).isFalse();
        }
    }
}
