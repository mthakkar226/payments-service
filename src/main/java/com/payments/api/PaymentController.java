package com.payments.api;

import com.payments.api.dto.PaymentRequest;
import com.payments.api.dto.PaymentResponse;
import com.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    @GetMapping
    public List<PaymentResponse> getByAccount(@RequestParam UUID accountId) {
        return paymentService.getByAccount(accountId);
    }
}
