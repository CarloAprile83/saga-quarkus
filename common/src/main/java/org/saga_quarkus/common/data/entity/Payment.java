package org.saga_quarkus.common.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payments", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    public Long id;

    @Column(name = "order_id", nullable = false)
    @JsonProperty("order_id")
    public Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    @JsonProperty("amount")
    @JsonDeserialize(using = org.saga_quarkus.common.kafka.DebeziumBigDecimalDeserializer.class)
    public BigDecimal amount;

    @Column(nullable = false)
    @JsonProperty("status")
    public String status;

    @CreationTimestamp
    @Column(updatable = false)
    @JsonProperty("timestamp")
    public OffsetDateTime timestamp;

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
}
