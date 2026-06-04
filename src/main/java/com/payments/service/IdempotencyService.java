package com.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.domain.IdempotencyKey;
import com.payments.repository.IdempotencyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Three branches:
     *  1. Same key + same hash  → return cached response (no-op)
     *  2. Same key + diff hash  → 409 Conflict
     *  3. New key               → execute supplier, persist key + response
     */
    public <T> T execute(String idempotencyKey, Object requestBody, Supplier<T> action, Class<T> responseType) {
        String hash = sha256(serialize(requestBody));
        Optional<IdempotencyKey> existing = idempotencyRepository.findById(idempotencyKey);

        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (!record.getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key already used with a different request payload");
            }
            // Same key + same hash: return cached response
            return deserialize(record.getResponseBody(), responseType);
        }

        // New key: execute and persist atomically (caller must be @Transactional)
        T response = action.get();
        IdempotencyKey record = new IdempotencyKey();
        record.setKey(idempotencyKey);
        record.setRequestHash(hash);
        record.setResponseBody(serialize(response));
        record.setStatusCode(HttpStatus.CREATED.value());
        idempotencyRepository.save(record);
        return response;
    }

    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Serialization failed", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Deserialization failed", e);
        }
    }
}
