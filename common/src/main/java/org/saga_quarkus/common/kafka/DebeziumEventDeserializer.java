package org.saga_quarkus.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ApplicationScoped
public class DebeziumEventDeserializer {
    private static final Logger log = LoggerFactory.getLogger(DebeziumEventDeserializer.class);
    
    @Inject
    ObjectMapper objectMapper;

    public <T> Optional<T> deserialize(String jsonPayload, Class<T> targetType) {
        try {
            if (jsonPayload == null || jsonPayload.isEmpty()) {
                return Optional.empty();
            }

            JsonNode eventNode = objectMapper.readTree(jsonPayload);
            JsonNode afterNode = eventNode.path("after");

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
