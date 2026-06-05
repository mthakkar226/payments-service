package com.payments.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.domain.ProcessedEvent;
import com.payments.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class LedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public LedgerConsumer(ProcessedEventRepository processedEventRepository,
                          ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Retry up to 3 attempts with exponential backoff (1s, 2s, 4s).
     * After all retries exhausted, message lands on payment-events-dlt.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-events", groupId = "payments-ledger")
    @Transactional
    public void consume(@Payload String message,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        UUID eventId = extractEventId(message);
        if (eventId == null) {
            log.warn("Received message with no parseable event id, skipping. key={}", key);
            return;
        }

        // Check processed_events — insert + apply in one transaction (at-least-once → effectively-once)
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate event {}, skipping", eventId);
            return;
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);

        applyToLedger(key, message);
        log.debug("Processed event {} for aggregate {}", eventId, key);
    }

    private void applyToLedger(String aggregateId, String payload) {
        // In a real system this would update a ledger/account balance.
        // Here we log the application — the dedup guarantee is the core contract.
        log.info("Ledger updated for payment {}: {}", aggregateId, payload);
    }

    private UUID extractEventId(String message) {
        try {
            var node = objectMapper.readTree(message);
            var idNode = node.get("id");
            if (idNode != null) return UUID.fromString(idNode.asText());
        } catch (Exception e) {
            log.error("Failed to extract event id from message: {}", e.getMessage());
        }
        return null;
    }
}
