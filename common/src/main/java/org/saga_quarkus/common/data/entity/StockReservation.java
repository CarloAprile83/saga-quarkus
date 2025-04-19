package org.saga_quarkus.common.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_reservations", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    public Long id;

    @Column(name = "order_id", nullable = false)
    @JsonProperty("order_id")
    public Long orderId;

    @Column(name = "product_id", nullable = false)
    @JsonProperty("product_id")
    public String productId;

    @Column(nullable = false)
    @JsonProperty("quantity")
    public Integer quantity;

    @Column(nullable = false)
    @JsonProperty("status")
    public String status; // e.g., RESERVED, FAILED, CANCELLED

    @CreationTimestamp
    @Column(updatable = false)
    @JsonProperty("timestamp")
    public OffsetDateTime timestamp;

    // Status constants
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED"; // For compensation (if needed)
}
