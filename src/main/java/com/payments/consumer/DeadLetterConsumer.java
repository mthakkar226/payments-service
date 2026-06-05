package com.payments.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final String DLT_TOPIC = "payment-events-dlt";

    private final MeterRegistry meterRegistry;

    public DeadLetterConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = DLT_TOPIC, groupId = "payments-ledger-dlt")
    public void handleDeadLetter(@Payload String message,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                 @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage) {
        log.error("Dead-letter event received. key={}, error={}, payload={}", key, errorMessage, message);

        // Increment a Prometheus counter so we can alert on DLT spikes
        meterRegistry.counter("payments.dlt.received", "topic", DLT_TOPIC).increment();
    }
}
