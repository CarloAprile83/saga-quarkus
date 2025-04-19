package org.saga_quarkus.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public class DebeziumEventDeserializer {
    private static final Logger log = LoggerFactory.getLogger(DebeziumEventDeserializer.class);

    public <T> Optional<T> deserialize(String jsonPayload, Class<T> targetType) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            log.debug("Raw JSON payload: {}", jsonPayload);
            if (jsonPayload == null || jsonPayload.isEmpty()) {
                return Optional.empty();
            }

            JsonNode eventNode = objectMapper.readTree(jsonPayload);
            JsonNode afterNode = eventNode.path("after");

            // Se "after" non esiste, prova con "payload"
            if ((afterNode.isMissingNode() || afterNode.isNull()) && eventNode.has("payload")) {
                afterNode = eventNode.path("payload");
            }

            if (afterNode.isMissingNode() || afterNode.isNull()) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.treeToValue(afterNode, targetType));
        } catch (Exception e) {
            log.error("Error deserializing Debezium event for {}: {}", targetType.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
