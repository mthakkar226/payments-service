package com.payments.integration;

import com.payments.api.dto.PaymentResponse;
import com.payments.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticLockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // PATCH-capable client (SimpleClientHttpRequestFactory doesn't support PATCH)
    private RestTemplate patchRestTemplate;

    @BeforeEach
    void setUp() {
        String baseUrl = restTemplate.getRootUri();
        patchRestTemplate = new RestTemplateBuilder()
                .rootUri(baseUrl)
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .build();
    }

    @Test
    void concurrentStatusTransition_onlyOneSucceeds() throws Exception {
        // Create a payment first
        String body = """
                {"accountId":"%s","amount":1000,"currency":"USD"}
                """.formatted(UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<PaymentResponse> createResponse = restTemplate.exchange(
                "/payments", HttpMethod.POST, new HttpEntity<>(body, headers), PaymentResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID paymentId = createResponse.getBody().getId();

        // Fire two concurrent PATCH requests — both try PENDING → AUTHORIZED
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures  = new AtomicInteger(0);

        String patchBody = """
                {"status":"AUTHORIZED"}
                """;
        HttpHeaders patchHeaders = new HttpHeaders();
        patchHeaders.setContentType(MediaType.APPLICATION_JSON);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    ResponseEntity<String> resp = patchRestTemplate.exchange(
                            "/payments/" + paymentId + "/status",
                            HttpMethod.PATCH,
                            new HttpEntity<>(patchBody, patchHeaders),
                            String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        startGate.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        // Exactly one thread wins; the other is rejected
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        // Final state must be AUTHORIZED
        ResponseEntity<PaymentResponse> finalState = restTemplate.getForEntity(
                "/payments/" + paymentId, PaymentResponse.class);
        assertThat(finalState.getBody().getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }
}
