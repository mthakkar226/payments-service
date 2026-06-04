package com.payments.outbox;

import com.payments.domain.OutboxEvent;
import com.payments.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String TOPIC = "payment-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxRepository.findUnpublished(batchSize);
        if (pending.isEmpty()) return;

        log.debug("Relaying {} outbox event(s) to Kafka", pending.size());

        for (OutboxEvent event : pending) {
            // send() returns a future; get() blocks until broker ACK (acks=all)
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload()).get();
                event.setPublished(true);
                outboxRepository.save(event);
                log.debug("Published outbox event {} (type={})", event.getId(), event.getEventType());
            } catch (Exception e) {
                // Leave published=false; next poll will retry
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}
