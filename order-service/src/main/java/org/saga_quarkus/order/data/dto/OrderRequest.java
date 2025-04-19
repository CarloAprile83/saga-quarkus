package org.saga_quarkus.order.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Using jakarta.validation might be good here, but keeping it simple for now
// import jakarta.validation.constraints.Min;
// import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    // @NotBlank
    public String productId;

    // @Min(1)
    public Integer quantity;

    // @NotBlank
    public String userId;
}
