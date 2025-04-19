package org.saga_quarkus.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature; // Importa SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ObjectMapperProducer {

    @Produces
    @ApplicationScoped
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Registra il modulo per i tipi Java 8 Date/Time (es. OffsetDateTime)
        mapper.registerModule(new JavaTimeModule());
        // Disabilita la scrittura delle date come timestamp numerici (forza ISO 8601)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Potresti aggiungere qui altre configurazioni di Jackson se necessario
        return mapper;
    }
}
