# Microservices Saga Application

This document outlines the architecture and components of a microservices application designed to demonstrate the Saga pattern for distributed transactions. The application is built using Quarkus and utilizes Kafka with Debezium for event-driven communication via Change Data Capture (CDC).

## 1. Overall Project Structure / Architecture

The application follows a microservices architecture. Key technologies include:

*   **Quarkus:** The framework used to build the individual microservices, offering fast startup times and low memory footprint.
*   **Kafka:** A distributed streaming platform used as the backbone for asynchronous event-driven communication between services.
*   **Debezium:** A CDC platform that captures row-level changes in each service's database and publishes them as events to Kafka topics. This allows services to react to data changes in other services without direct API calls.

The system comprises the following main services and a common module:

*   **`order-service`**: Manages the lifecycle of customer orders and orchestrates the Saga.
*   **`payment-service`**: Handles payment processing and compensation (refunds).
*   **`stock-service`**: Manages stock reservation for orders.
*   **`common`**: A shared module containing common data entities and configurations used across different services.

## 2. `common` Module

*   **Purpose:** The `common` module provides shared data entities (POJOs) and configurations to ensure consistency and reduce code duplication across the microservices.

*   **Key Contents:**
    *   **Entities:**
        *   `Order`: Represents a customer order.
            *   Fields: `id`, `productId`, `quantity`, `userId`, `status`, `creationTimestamp`, `lastUpdateTimestamp`.
            *   Example `Order.status` values defined in `Order.java`: `PENDING`, `AWAITING_STOCK`, `COMPLETED`, `FAILED`, `COMPENSATING_PAYMENT`. (Note: The Saga orchestration might involve more transient statuses not explicitly defined as constants in the entity, depending on the complexity of the flow.)
        *   `Payment`: Represents a payment transaction.
            *   Fields: `id`, `orderId`, `amount`, `status`, `timestamp`.
            *   Example `Payment.status` values defined in `Payment.java`: `COMPLETED`, `FAILED`, `CANCELLED`.
        *   `StockReservation`: Represents a reservation of stock for an order.
            *   Fields: `id`, `orderId`, `productId`, `quantity`, `status`, `timestamp`.
            *   Example `StockReservation.status` values defined in `StockReservation.java`: `RESERVED`, `FAILED`, `CANCELLED`.
    *   **Configuration:**
        *   `ObjectMapperProducer`: Provides a customized Jackson `ObjectMapper` instance for consistent JSON serialization/deserialization across services.
        *   `DebeziumEventDeserializer`: A Kafka deserializer specifically designed to handle the structure of Debezium change events, extracting the `after` state of the changed row.
        *   `DebeziumBigDecimalDeserializer`: A Kafka deserializer to correctly handle `BigDecimal` fields from Debezium events, which might otherwise be deserialized as `double` with potential precision loss.

## 3. `order-service`

*   **Purpose:** This service is responsible for managing the lifecycle of customer orders. It acts as the orchestrator for the Saga pattern, coordinating actions across the `payment-service` and `stock-service`.

*   **Key Components:**
    *   `OrderResource.java`:
        *   Exposes a REST API endpoint (POST `/orders`) for clients to create new orders.
        *   When a new order is created, its initial status is set to `PENDING`.
    *   `OrderSagaOrchestrator.java`:
        *   This is the core of the Saga logic.
        *   It consumes events from the `payment-events` and `stock-events` Kafka topics. These topics are fed by Debezium, capturing data changes from the `payment-service` and `stock-service` databases, respectively.
        *   Based on the incoming events (e.g., payment success/failure, stock reservation success/failure), it updates the `Order` status. This progression of status changes drives the Saga forward (e.g., from `PENDING` to `AWAITING_PAYMENT` to `AWAITING_STOCK` to `CONFIRMED`) or initiates compensation actions if a step fails (e.g., setting status to `COMPENSATING_PAYMENT` if stock reservation fails after payment).

*   **Interaction:**
    *   Publishes changes to its own `Order` entities to the `order-events` Kafka topic. Debezium CDC on the `order-service` database captures these changes.
    *   Consumes events from `payment-events` (for payment status updates) and `stock-events` (for stock reservation status updates).

## 4. `payment-service`

*   **Purpose:** This service manages all aspects of payment processing, including handling initial payments and processing refunds as part of Saga compensation.

*   **Key Components:**
    *   `PaymentProcessor.java`:
        *   Consumes events from the `order-events` Kafka topic.
        *   When an `Order` event is received with `Order.status = PENDING` (or a specific status indicating payment is due, like `AWAITING_PAYMENT`):
            *   It attempts to process the payment (simulated logic).
            *   It then creates a `Payment` record in its own database with `Payment.status = COMPLETED` or `Payment.status = FAILED`.
        *   When an `Order` event is received with `Order.status = COMPENSATING_PAYMENT`:
            *   It attempts to process a refund (simulated logic).
            *   It updates the corresponding `Payment` record's status to `Payment.status = CANCELLED`.

*   **Interaction:**
    *   Publishes changes to its `Payment` entities to the `payment-events` Kafka topic (via Debezium CDC on its database).
    *   Consumes events from `order-events`.

*   **Note:** This service does not expose a direct HTTP API for payment operations. All actions are triggered by consuming `order-events`. The payment and refund logic is simulated for the purpose of this demonstration.

## 5. `stock-service`

*   **Purpose:** This service is responsible for managing stock reservations for items included in customer orders.

*   **Key Components:**
    *   `StockProcessor.java`:
        *   Consumes events from the `order-events` Kafka topic.
        *   When an `Order` event is received with `Order.status = AWAITING_STOCK`:
            *   It attempts to reserve the required stock (simulated logic).
            *   It creates a `StockReservation` record in its database with `StockReservation.status = RESERVED` (if stock is available) or `StockReservation.status = FAILED` (if stock is insufficient).

*   **Interaction:**
    *   Publishes changes to its `StockReservation` entities to the `stock-events` Kafka topic (via Debezium CDC on its database).
    *   Consumes events from `order-events`.

*   **Note:** This service does not expose a direct HTTP API for stock operations. All actions are triggered by consuming `order-events`. The stock availability check and reservation logic is simulated. Currently, this service does not have explicit logic to cancel stock reservations based on an `Order` event (i.e., compensation for stock is not actively triggered by this service based on an event like `Order.status = COMPENSATING_STOCK`).

## 6. Data Flow and Saga Orchestration (Happy Path)

This section describes the sequence of events, service interactions, and entity state changes for a successful order, driven by Debezium CDC events.

**Event Sources & Topics:**
*   **`order-events`**: Carries `Order` entity changes from `order-service`'s database. Produced by Debezium.
*   **`payment-events`**: Carries `Payment` entity changes from `payment-service`'s database. Produced by Debezium.
*   **`stock-events`**: Carries `StockReservation` entity changes from `stock-service`'s database. Produced by Debezium.

**Service Subscriptions:**
*   `order-service` (specifically `OrderSagaOrchestrator`): Subscribes to `payment-events` and `stock-events`.
*   `payment-service` (specifically `PaymentProcessor`): Subscribes to `order-events`.
*   `stock-service` (specifically `StockProcessor`): Subscribes to `order-events`.

**Flow:**

1.  **Client Creates Order:**
    *   **Action:** A client sends a POST request to `order-service` (`/orders`).
    *   **`order-service`:** `OrderResource` creates an `Order` entity in its local database with **`Order.status = PENDING`**.
    *   **Event:** Debezium captures this `Order` creation and publishes the new `Order` data to the **`order-events`** Kafka topic.

2.  **Payment Service Processes Payment:**
    *   **`payment-service` (`PaymentProcessor`):** Consumes the `Order` event from `order-events`.
    *   **Action:** Recognizing `Order.status = PENDING`, it simulates payment processing.
    *   **`payment-service`:** Creates a `Payment` entity in its local database with **`Payment.status = COMPLETED`** (assuming success) and the relevant `orderId`.
    *   **Event:** Debezium captures this `Payment` creation and publishes the new `Payment` data to the **`payment-events`** Kafka topic.

3.  **Order Service Updates Order Status (Post-Payment):**
    *   **`order-service` (`OrderSagaOrchestrator`):** Consumes the `Payment` event from `payment-events`.
    *   **Action:** Validates `Payment.status = COMPLETED`.
    *   **`order-service`:** Updates the corresponding `Order` entity in its database to **`Order.status = AWAITING_STOCK`**.
    *   **Event:** Debezium captures this `Order` status update and publishes the updated `Order` data to the **`order-events`** Kafka topic.

4.  **Stock Service Reserves Stock:**
    *   **`stock-service` (`StockProcessor`):** Consumes the `Order` event from `order-events`.
    *   **Action:** Recognizing `Order.status = AWAITING_STOCK`, it simulates stock reservation.
    *   **`stock-service`:** Creates a `StockReservation` entity in its local database with **`StockReservation.status = RESERVED`** (assuming success) and the relevant `orderId`.
    *   **Event:** Debezium captures this `StockReservation` creation and publishes the new `StockReservation` data to the **`stock-events`** Kafka topic.

5.  **Order Service Confirms Order (Saga Completion):**
    *   **`order-service` (`OrderSagaOrchestrator`):** Consumes the `StockReservation` event from `stock-events`.
    *   **Action:** Validates `StockReservation.status = RESERVED`.
    *   **`order-service`:** Updates the corresponding `Order` entity in its database to **`Order.status = COMPLETED`**.
    *   **Event:** Debezium captures this `Order` status update and publishes the updated `Order` data to the **`order-events`** Kafka topic.

6.  **Order Fulfilled (Conceptual):**
    *   The Saga is now complete and the order is **`COMPLETED`**. Subsequent business processes like shipping can now proceed. The `Order.status` might later transition to `SHIPPED`, which would also be published to `order-events` for any interested downstream services.

## 7. Data Flow and Saga Orchestration (Compensation Path)

This section describes an example scenario where a step in the Saga fails (stock reservation fails after successful payment), triggering compensation. State changes in entities are key to this process.

**Event Sources & Topics:** (Same as Happy Path)
**Service Subscriptions:** (Same as Happy Path)

**Flow (Example: Stock Reservation Failure):**

1.  **Client Creates Order & Payment Processed Successfully:**
    *   Identical to steps 1-3 of the "Happy Path":
        *   Client creates order via `order-service`. `Order` is created with **`Order.status = PENDING`**. (Event on `order-events`)
        *   `payment-service` consumes, processes payment. `Payment` is created with **`Payment.status = COMPLETED`**. (Event on `payment-events`)
        *   `order-service` consumes payment confirmation, updates order to **`Order.status = AWAITING_STOCK`**. (Event on `order-events`)

2.  **Stock Service Fails to Reserve Stock:**
    *   **`stock-service` (`StockProcessor`):** Consumes the `Order` event from `order-events` where `Order.status = AWAITING_STOCK`.
    *   **Action:** Attempts to reserve stock but encounters an issue (e.g., insufficient items - simulated failure).
    *   **`stock-service`:** Creates a `StockReservation` entity in its local database with **`StockReservation.status = FAILED`** and the `orderId`.
    *   **Event:** Debezium captures this `StockReservation` creation and publishes the `StockReservation` data (with `status = FAILED`) to the **`stock-events`** Kafka topic.

3.  **Order Service Initiates Compensation (Payment Refund):**
    *   **`order-service` (`OrderSagaOrchestrator`):** Consumes the `StockReservation` event from `stock-events`.
    *   **Action:** Recognizes `StockReservation.status = FAILED`, indicating the Saga must be compensated.
    *   **`order-service`:** Updates the corresponding `Order` entity in its database to **`Order.status = COMPENSATING_PAYMENT`**.
    *   **Event:** Debezium captures this `Order` status update and publishes the updated `Order` data to the **`order-events`** Kafka topic. This event signals to other services that this order requires compensation.

4.  **Payment Service Processes Refund (Compensation Logic):**
    *   **`payment-service` (`PaymentProcessor`):** Consumes the `Order` event from `order-events`.
    *   **Action:** Recognizing `Order.status = COMPENSATING_PAYMENT`, it identifies that a refund is required for the associated `orderId`. It simulates refund processing.
    *   **`payment-service`:** Updates the existing `Payment` entity in its local database, changing its status from `COMPLETED` to **`Payment.status = CANCELLED`**.
    *   **Event:** Debezium captures this `Payment` status update and publishes the updated `Payment` data to the **`payment-events`** Kafka topic.

5.  **Order Service Marks Order as Failed (Saga Compensation Complete):**
    *   **`order-service` (`OrderSagaOrchestrator`):** Consumes the `Payment` event from `payment-events`.
    *   **Action:** Validates `Payment.status = CANCELLED` (indicating successful payment compensation).
    *   **`order-service`:** Updates the corresponding `Order` entity in its database to **`Order.status = FAILED`**.
    *   **Event:** Debezium captures this `Order` status update and publishes the updated `Order` data to the **`order-events`** Kafka topic.

6.  **Saga Completion (Order Failed):**
    *   The Saga is now complete. The order is officially **`FAILED`**, and the payment has been successfully refunded. All relevant state changes have been captured and broadcast via Debezium and Kafka.

## 8. Running the Application

Follow these steps to get the application and its infrastructure up and running.

*   **Prerequisites:**
    *   Java (JDK 17+ recommended)
    *   Maven
    *   Docker
    *   Docker Compose

*   **Steps:**

    1.  **Clone the Repository:**
        ```bash
        git clone <repository-url>
        cd <repository-name>
        ```

    2.  **Build the `common` Module:**
        This module contains shared classes used by other services.
        ```bash
        cd common
        mvn clean install
        cd ..
        ```

    3.  **Start Infrastructure with Docker Compose:**
        Navigate to the `docker` directory which should contain a `docker-compose.yml` file. This file defines Kafka, ZooKeeper, PostgreSQL instances for each service, and Debezium Connect.
        ```bash
        cd docker
        docker-compose up -d
        ```
        This will start all necessary infrastructure components in detached mode.

    4.  **Verify Debezium Connectors:**
        Debezium connectors are responsible for monitoring database changes and publishing them to Kafka. You need to configure a connector for each service's database (`orders`, `payments`, `stock`).

        *   **Check existing connectors** (wait a minute or two for Kafka Connect to start):
            ```bash
            curl -H "Accept:application/json" localhost:8083/connectors/
            ```

        *   **Example Connector Configuration (e.g., for `order-service`):**
            Save the following JSON as `order-connector.json`:
            ```json
            {
              "name": "order-connector",
              "config": {
                "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                "database.hostname": "postgres-order",
                "database.port": "5432",
                "database.user": "orderuser",
                "database.password": "orderpass",
                "database.dbname": "orderdb",
                "database.server.name": "orderserver",
                "table.include.list": "public.orders",
                "topic.prefix": "order",
                "plugin.name": "pgoutput",
                "value.converter": "org.apache.kafka.connect.json.JsonConverter",
                "value.converter.schemas.enable": "false",
                "transforms": "unwrap",
                "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
                "transforms.unwrap.drop.tombstones": "false"
              }
            }
            ```
            *   **Note on `topic.prefix`**: In the provided configurations within the service `application.properties` (e.g., `mp.messaging.incoming.order-events.topic=orderserver.public.orders`), the Debezium topic name is often `database.server.name` + `table.include.list`. The example above uses `topic.prefix` for finer control, resulting in topics like `order.public.orders`. Adjust your Kafka consumer configurations in `application.properties` or the Debezium connector (`topic.prefix` or ensure `database.server.name` matches the expected prefix) so they align. For this documentation, we assume Debezium generates topics like `orderserver.public.orders`, `paymentserver.public.payments`, etc. and the service consumers are configured accordingly. The connector examples below will use `database.server.name` which results in these topic names.

        *   **Create Connectors (if not automatically created or if you need to modify):**
            You'll need similar JSON configuration files for `payment-connector.json` and `stock-connector.json`, adjusting the `name`, `database.*` properties, `database.server.name`, and `table.include.list`.

            **`payment-connector.json`:**
            ```json
            {
              "name": "payment-connector",
              "config": {
                "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                "database.hostname": "postgres-payment",
                "database.port": "5432",
                "database.user": "paymentuser",
                "database.password": "paymentpass",
                "database.dbname": "paymentdb",
                "database.server.name": "paymentserver",
                "table.include.list": "public.payments",
                "plugin.name": "pgoutput",
                "value.converter": "org.apache.kafka.connect.json.JsonConverter",
                "value.converter.schemas.enable": "false",
                "transforms": "unwrap",
                "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
                "transforms.unwrap.drop.tombstones": "false"
              }
            }
            ```

            **`stock-connector.json`:**
            ```json
            {
              "name": "stock-connector",
              "config": {
                "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                "database.hostname": "postgres-stock",
                "database.port": "5432",
                "database.user": "stockuser",
                "database.password": "stockpass",
                "database.dbname": "stockdb",
                "database.server.name": "stockserver",
                "table.include.list": "public.stock_reservations",
                "plugin.name": "pgoutput",
                "value.converter": "org.apache.kafka.connect.json.JsonConverter",
                "value.converter.schemas.enable": "false",
                "transforms": "unwrap",
                "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
                "transforms.unwrap.drop.tombstones": "false"
              }
            }
            ```

            To create/update each connector:
            ```bash
            curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d @order-connector.json
            curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d @payment-connector.json
            curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d @stock-connector.json
            ```
            *Ensure the database services (`postgres-order`, `postgres-payment`, `postgres-stock`) are fully initialized before creating connectors. If you get errors, wait a bit and retry.*

    5.  **Build and Run Each Microservice:**
        Open a new terminal for each service.

        *   **Order Service:**
            ```bash
            cd order-service
            mvn quarkus:dev
            ```
        *   **Payment Service:**
            ```bash
            cd payment-service
            mvn quarkus:dev
            ```
        *   **Stock Service:**
            ```bash
            cd stock-service
            mvn quarkus:dev
            ```
        These commands compile the services and run them in development mode, which supports hot reloading.

    6.  **(Optional) Build Native Executables:**
        For production-like environments, you can build native executables.
        ```bash
        # Example for order-service
        cd order-service
        mvn package -Pnative
        ./target/order-service-1.0.0-SNAPSHOT-runner # Adjust version as needed
        cd ..
        ```
        Repeat for other services. Native executables need to be configured to connect to the Dockerized PostgreSQL and Kafka instances (this is typically handled in `application.properties`).

## 9. Testing the Saga

Once all services and infrastructure are running:

1.  **Create a New Order:**
    Use `curl` or a tool like Postman to send a POST request to the `order-service`.
    ```bash
    curl -X POST -H "Content-Type: application/json" \
    -d '{
      "customerId": "customer123",
      "itemId": "itemA",
      "quantity": 1,
      "totalAmount": 99.99
    }' \
    http://localhost:8080/orders
    ```
    This should return the created order with an initial status (e.g., `PENDING`) and an ID. Note the `orderId`.

2.  **Observe the Flow:**

    *   **Check Service Logs:** Each service running with `mvn quarkus:dev` will output logs to its console. Observe the messages indicating event consumption and processing.
        *   `order-service` logs will show order creation, consumption of payment and stock events, and order status updates.
        *   `payment-service` logs will show consumption of order events and payment processing attempts.
        *   `stock-service` logs will show consumption of order events and stock reservation attempts.

    *   **Check Kafka Topics:**
        You can use a command-line tool like `kcat` (formerly `kafkacat`) or a GUI tool to inspect messages on Kafka topics.
        *   Install `kcat`: `sudo apt install kcat` or use a Docker image.
        *   Example: Listen to messages on the `paymentserver.public.payments` topic (assuming your Debezium connector for payments is named `paymentserver` and it's monitoring the `public.payments` table. The topic name is typically `<database.server.name>.<schema_name>.<table_name>`):
            ```bash
            kcat -b localhost:9092 -t paymentserver.public.payments -C -J
            ```
            (Use `-t orderserver.public.orders` for order events, `-t stockserver.public.stock_reservations` for stock events. Adjust topic names based on your Debezium connector configuration, specifically `database.server.name`, the schema, and the table names.)
            You should see JSON messages representing the database changes captured by Debezium.

    *   **Check Database Tables:**
        Connect to the PostgreSQL databases directly to see the records and their statuses.
        *   `orderdb` on `localhost:5433` (user/pass: `orderuser`/`orderpass`)
        *   `paymentdb` on `localhost:5434` (user/pass: `paymentuser`/`paymentpass`)
        *   `stockdb` on `localhost:5435` (user/pass: `stockuser`/`stockpass`)
        Example using `psql`:
        ```bash
        psql -h localhost -p 5433 -U orderuser -d orderdb -c "SELECT * FROM orders;"
        psql -h localhost -p 5434 -U paymentuser -d paymentdb -c "SELECT * FROM payments;"
        psql -h localhost -p 5435 -U stockuser -d stockdb -c "SELECT * FROM stock_reservations;"
        ```

3.  **Simulate Failures to Test Compensation:**

    *   **Simulate Stock Unavailability:**
        1.  Before creating an order, you could (if the application had such a feature) reduce available stock for a particular `itemId` in `stock-service`'s database to zero.
        2.  Create an order for that `itemId`.
        3.  Observe the Saga:
            *   Order created (`PENDING`).
            *   Payment processed (`Payment.status = COMPLETED` in `payment-service`, `Order.status` becomes `AWAITING_STOCK`).
            *   Stock reservation fails (`StockReservation.status = FAILED` in `stock-service`).
            *   `order-service` changes `Order.status` to `COMPENSATING_PAYMENT`.
            *   `payment-service` processes refund (`Payment.status = CANCELLED` in `payment-service`).
            *   `order-service` changes `Order.status` to `FAILED`.
        *   Since direct stock manipulation isn't built-in via an API for this demo, you might need to modify the `StockProcessor` logic temporarily to force a failure for certain items, or manually update the database if you're quick enough between steps.

## 10. Future Improvements / Considerations

This application serves as a demonstration of the Saga pattern. For a production-ready system, several other aspects would need to be addressed:

*   **Idempotency in Message Consumers:** Ensure that processing the same message multiple times (e.g., due to Kafka redelivery) does not have unintended side effects. This often involves checking if an operation has already been performed based on a unique message ID or the state of the entities.
*   **Dead Letter Queues (DLQs):** Implement DLQs to handle messages that consistently fail processing after several retries. This prevents poison pills from blocking Kafka topic consumption and allows for later investigation.
*   **More Robust Error Handling and Retry Mechanisms:** Implement configurable retry policies for transient errors (e.g., network issues when communicating with a database) within the message consumers.
*   **Security Considerations:**
    *   **Authentication/Authorization:** Secure service-to-service communication and external API endpoints.
    *   **Kafka Security:** Secure Kafka brokers and topics (e.g., using SASL).
*   **Distributed Tracing and Monitoring:** Integrate tools like OpenTelemetry, Jaeger, or Zipkin for tracing requests across microservices. Use Prometheus and Grafana for monitoring service health and metrics.
*   **Implementing Actual Compensation in `stock-service`:** The current `stock-service` doesn't actively compensate (e.g., release a reservation by changing `StockReservation.status` to `CANCELLED`) based on an `Order` event like `Order.status = COMPENSATING_STOCK` (a hypothetical status if stock compensation were the first step). This logic would be necessary for a complete Saga where stock is reserved before payment.
*   **Dedicated Saga Log Table:** Instead of or in addition to relying solely on the `Order.status` field, `order-service` could maintain a dedicated Saga log table. This table would record each step of the Saga, its status, and any compensation actions taken, providing better auditability and recovery capabilities.
*   **Containerizing Microservices:** The `docker-compose.yml` currently only handles the infrastructure. For easier deployment and orchestration (e.g., with Kubernetes), the Quarkus microservices themselves should also be containerized.
*   **Configuration Management:** Externalize configurations using tools like HashiCorp Consul or Spring Cloud Config Server, especially for sensitive information and settings that vary across environments.
*   **Database Schema Management:** Use tools like Flyway or Liquibase for managing database schema migrations.
*   **Testing Strategies:** Expand testing to include integration tests that cover the entire Saga flow and contract testing between services.
*   **Asynchronous API for Order Creation:** Consider making the initial order creation API (POST `/orders`) asynchronous, where it accepts the request, validates it, creates a PENDING order, and returns an immediate acknowledgment (e.g., HTTP 202 Accepted with a link to track the order status). The actual Saga processing would then happen entirely in the background.
