package org.saga_quarkus.order.rest;

import jakarta.inject.Inject;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.saga_quarkus.common.data.entity.Order;
import org.saga_quarkus.order.data.dto.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);

    // Inject a service/bean later if more complex logic is needed
    // For now, direct persistence is simple enough

    @POST
    @Transactional // Ensure the operation is atomic
    public Response createOrder(OrderRequest orderRequest) {
        log.info("Received order request: {}", orderRequest); // Add this line
        if (orderRequest == null || orderRequest.productId == null || orderRequest.quantity == null || orderRequest.userId == null || orderRequest.quantity <= 0) {
            log.warn("Received invalid order request: {}", orderRequest);
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid order data provided.").build();
        }

        log.info("Received order request: {}", orderRequest);

        Order newOrder = Order.builder()
                .productId(orderRequest.productId)
                .quantity(orderRequest.quantity)
                .userId(orderRequest.userId)
                .status(Order.STATUS_PENDING) // Initial status
                .build();

        // Persist the new order using Panache active record pattern
        try {
            newOrder.persist();
            log.info("Order created successfully with ID: {}", newOrder.id);
            // Return the created order (or just its ID)
            return Response.status(Response.Status.CREATED).entity(newOrder).build();
        } catch (Exception e) {
            log.error("Error persisting order: {}", orderRequest, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to create order.").build();
        }
    }
}
