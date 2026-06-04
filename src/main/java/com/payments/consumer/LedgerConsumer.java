package com.payments.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.domain.OutboxEvent;
import com.payments.domain.ProcessedEvent;
import com.payments.repository.OutboxRepository;
import com.payments.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class LedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LedgerConsumer(ProcessedEventRepository processedEventRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "payments-ledger")
    @Transactional
    public void consume(@Payload String message,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        Acknowledgment ack) {
        UUID eventId = extractEventId(message);
        if (eventId == null) {
            log.warn("Received message with no event id, skipping. key={}", key);
            ack.acknowledge();
            return;
        }

        // Check processed_events — insert + apply in one transaction (at-least-once → effectively-once)
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate event {}, skipping", eventId);
            ack.acknowledge();
            return;
        }

        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processedEventRepository.save(processed);

        applyToLedger(key, message);

        ack.acknowledge();
        log.debug("Processed event {} for aggregate {}", eventId, key);
    }

    private void applyToLedger(String aggregateId, String payload) {
        // In a real system this would update a ledger/account balance.
        // Here we log the application — the dedup guarantee is the core contract.
        log.info("Ledger updated for payment {}: {}", aggregateId, payload);
    }

    private UUID extractEventId(String message) {
        // The outbox payload is a serialized PaymentResponse; we use the payment id as the event id.
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
