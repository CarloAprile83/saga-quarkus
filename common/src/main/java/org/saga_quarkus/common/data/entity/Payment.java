package org.saga_quarkus.common.data.entity;

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
    public Long id;

    @Column(name = "order_id", nullable = false)
    public Long orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public String status; // e.g., COMPLETED, FAILED, CANCELLED

    @CreationTimestamp
    @Column(updatable = false)
    public OffsetDateTime timestamp;

    // Status constants
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED"; // For compensation
}
