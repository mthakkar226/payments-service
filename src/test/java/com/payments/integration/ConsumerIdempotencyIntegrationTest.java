package com.payments.integration;

import com.payments.api.dto.PaymentResponse;
import com.payments.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ConsumerIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void paymentCreated_eventProcessedExactlyOnce() throws Exception {
        // Create a payment — this writes an outbox event
        UUID accountId = UUID.randomUUID();
        String body = """
                {"accountId":"%s","amount":750,"currency":"GBP"}
                """.formatted(accountId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/payments", HttpMethod.POST, new HttpEntity<>(body, headers), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID paymentId = response.getBody().getId();

        // Wait for the outbox relay to publish to Kafka and the consumer to process it
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(processedEventRepository.existsById(paymentId)).isTrue()
                );

        // Verify exactly one processed_events record — not two
        long count = processedEventRepository.findAll().stream()
                .filter(e -> e.getEventId().equals(paymentId))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
