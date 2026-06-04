package com.payments.service;

import com.payments.api.dto.PaymentRequest;
import com.payments.api.dto.PaymentResponse;
import com.payments.domain.Payment;
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

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        Payment payment = new Payment();
        payment.setAccountId(request.getAccountId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency().toUpperCase());
        return PaymentResponse.from(paymentRepository.save(payment));
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
}
