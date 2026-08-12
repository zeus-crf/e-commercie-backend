package com.ecommercie.outbox.service;

import com.ecommercie.outbox.models.OutboxEvent;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void registrar(String type, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        outboxEventRepository.save(OutboxEvent.of(type, json));
    }
}
