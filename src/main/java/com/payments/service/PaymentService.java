package com.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.api.dto.PaymentRequest;
import com.payments.api.dto.PaymentResponse;
import com.payments.domain.OutboxEvent;
import com.payments.domain.Payment;
import com.payments.domain.PaymentStatus;
import com.payments.repository.OutboxRepository;
import com.payments.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPayment(String idempotencyKey, PaymentRequest request) {
        return idempotencyService.execute(
                idempotencyKey,
                request,
                () -> {
                    Payment payment = new Payment();
                    payment.setAccountId(request.getAccountId());
                    payment.setAmount(request.getAmount());
                    payment.setCurrency(request.getCurrency().toUpperCase());
                    Payment saved = paymentRepository.save(payment);

                    // Write outbox event atomically in the same transaction
                    outboxRepository.save(buildOutboxEvent(saved));

                    return PaymentResponse.from(saved);
                },
                PaymentResponse.class
        );
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getByAccount(UUID accountId) {
        return paymentRepository.findByAccountId(accountId)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional
    public PaymentResponse transitionStatus(UUID id, PaymentStatus targetStatus) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));

        payment.getStatus().assertCanTransitionTo(targetStatus);
        payment.setStatus(targetStatus);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private OutboxEvent buildOutboxEvent(Payment payment) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setAggregateId(payment.getId());
            event.setEventType("PAYMENT_CREATED");
            event.setPayload(objectMapper.writeValueAsString(PaymentResponse.from(payment)));
            return event;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
