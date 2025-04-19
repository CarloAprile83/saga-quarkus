package org.saga_quarkus.common.kafka;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

public class DebeziumBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String base64 = p.getText();
        if (base64 == null) return null;
        byte[] bytes = Base64.getDecoder().decode(base64);
        // Debezium/Connect encodes decimals as big-endian two's complement
        BigInteger unscaled = new BigInteger(bytes);
        // Scala hardcoded a 2 (vedi schema payments), oppure puoi parametrizzare
        return new BigDecimal(unscaled, 2);
    }
}