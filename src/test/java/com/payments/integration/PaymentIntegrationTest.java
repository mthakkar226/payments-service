package com.payments.integration;

import com.payments.api.dto.PaymentResponse;
import com.payments.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAndGetPayment_fullFlow() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"accountId":"%s","amount":5000,"currency":"USD"}
                """.formatted(UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        // POST — create payment
        ResponseEntity<PaymentResponse> createResponse = restTemplate.exchange(
                "/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                PaymentResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getAmount()).isEqualTo(5000L);
        assertThat(created.getCurrency()).isEqualTo("USD");

        // GET by ID
        ResponseEntity<PaymentResponse> getResponse = restTemplate.getForEntity(
                "/payments/" + created.getId(), PaymentResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getId()).isEqualTo(created.getId());
    }

    @Test
    void duplicateRequest_sameKeyAndBody_returnsCachedResponse() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = """
                {"accountId":"%s","amount":1000,"currency":"EUR"}
                """.formatted(UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        // First request
        ResponseEntity<PaymentResponse> first = restTemplate.exchange(
                "/payments", HttpMethod.POST, request, PaymentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second request — same key + same body → cached 201, same payment id
        ResponseEntity<PaymentResponse> second = restTemplate.exchange(
                "/payments", HttpMethod.POST, request, PaymentResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().getId()).isEqualTo(first.getBody().getId());
    }

    @Test
    void duplicateRequest_sameKeyDifferentBody_returns409() {
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        String body1 = """
                {"accountId":"%s","amount":1000,"currency":"USD"}
                """.formatted(UUID.randomUUID());

        String body2 = """
                {"accountId":"%s","amount":9999,"currency":"GBP"}
                """.formatted(UUID.randomUUID());

        // First request succeeds
        restTemplate.exchange("/payments", HttpMethod.POST,
                new HttpEntity<>(body1, headers), PaymentResponse.class);

        // Second request with same key but different body → 409
        ResponseEntity<String> conflict = restTemplate.exchange("/payments", HttpMethod.POST,
                new HttpEntity<>(body2, headers), String.class);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingIdempotencyKey_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No Idempotency-Key header

        String body = """
                {"accountId":"%s","amount":500,"currency":"USD"}
                """.formatted(UUID.randomUUID());

        ResponseEntity<String> response = restTemplate.exchange("/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getByAccountId_returnsPaymentsForAccount() {
        UUID accountId = UUID.randomUUID();
        String body = """
                {"accountId":"%s","amount":2500,"currency":"USD"}
                """.formatted(accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        restTemplate.exchange("/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers), PaymentResponse.class);

        ResponseEntity<PaymentResponse[]> listResponse = restTemplate.getForEntity(
                "/payments?accountId=" + accountId, PaymentResponse[].class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].getAccountId()).isEqualTo(accountId);
    }
}
