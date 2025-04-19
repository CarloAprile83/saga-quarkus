package org.saga_quarkus.common.data.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "orders", schema = "public")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    public Long id;

    @Column(name = "product_id", nullable = false)
    @JsonProperty("product_id")
    public String productId;

    @Column(nullable = false)
    @JsonProperty("quantity")
    public Integer quantity;

    @Column(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    public String userId;

    @Column(nullable = false)
    @JsonProperty("status")
    public String status; // e.g., PENDING, AWAITING_STOCK, COMPLETED, FAILED, COMPENSATING_PAYMENT

    @CreationTimestamp
    @Column(name = "creation_timestamp", updatable = false)
    @JsonProperty("creation_timestamp")
    public OffsetDateTime creationTimestamp;

    @UpdateTimestamp
    @Column(name = "last_update_timestamp")
    @JsonProperty("last_update_timestamp")
    public OffsetDateTime lastUpdateTimestamp;

    // Enum for status might be better, but using String as per requirement
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AWAITING_STOCK = "AWAITING_STOCK";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_COMPENSATING_PAYMENT = "COMPENSATING_PAYMENT";
    // Add other statuses if needed based on saga flow

    // Convenience method to update status
    public void updateStatus(String newStatus) {
        this.status = newStatus;
        // Panache will automatically handle the update timestamp
        persist(); // Or merge() if detached
    }
}
