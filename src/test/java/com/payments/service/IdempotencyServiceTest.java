package com.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.domain.IdempotencyKey;
import com.payments.repository.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    private IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
    }

    @Test
    void newKey_executesActionAndPersistsRecord() {
        when(idempotencyRepository.findById("key-1")).thenReturn(Optional.empty());
        when(idempotencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = idempotencyService.execute("key-1", "payload", () -> "response", String.class);

        assertThat(result).isEqualTo("response");
        verify(idempotencyRepository).save(argThat(r -> r.getKey().equals("key-1")));
    }

    @Test
    void sameKeyAndSameHash_returnsCachedResponse() throws Exception {
        String payload = "payload";
        String hash = idempotencyService.sha256(objectMapper.writeValueAsString(payload));
        String cachedJson = objectMapper.writeValueAsString("cached-response");

        IdempotencyKey record = new IdempotencyKey();
        record.setKey("key-2");
        record.setRequestHash(hash);
        record.setResponseBody(cachedJson);

        when(idempotencyRepository.findById("key-2")).thenReturn(Optional.of(record));

        String result = idempotencyService.execute("key-2", payload, () -> "new-response", String.class);

        assertThat(result).isEqualTo("cached-response");
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    void sameKeyDifferentHash_throws409Conflict() {
        IdempotencyKey record = new IdempotencyKey();
        record.setKey("key-3");
        record.setRequestHash("different-hash");

        when(idempotencyRepository.findById("key-3")).thenReturn(Optional.of(record));

        assertThatThrownBy(() ->
                idempotencyService.execute("key-3", "different-payload", () -> "response", String.class)
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different request payload");
    }
}
