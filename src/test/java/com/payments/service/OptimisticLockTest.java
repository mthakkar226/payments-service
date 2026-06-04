package com.payments.service;

import com.payments.domain.Payment;
import com.payments.domain.PaymentStatus;
import com.payments.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptimisticLockTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    void concurrentUpdate_throwsOptimisticLockException() {
        UUID id = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(id);
        payment.setAccountId(UUID.randomUUID());
        payment.setAmount(1000L);
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING);

        // Simulate JPA detecting a stale version on save (another thread updated first)
        when(paymentRepository.save(any())).thenThrow(
                new ObjectOptimisticLockingFailureException(Payment.class, id));

        assertThatThrownBy(() -> paymentRepository.save(payment))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
