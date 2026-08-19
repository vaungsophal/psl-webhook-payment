# Webhook Sequence Diagrams

This document shows the main webhook flows for the payment webhook learning project.

## 1. Successful Payment Webhook

```mermaid
sequenceDiagram
    autonumber
    participant U as Customer
    participant V as Vue Web App
    participant C as Commerce API
    participant P as Mock Payment Provider
    participant DB as PostgreSQL
    participant Q as RabbitMQ

    U->>V: Create order
    V->>C: POST /api/orders
    C->>DB: Save order with CREATED status
    DB-->>C: Order saved
    C-->>V: Order created

    U->>V: Start payment
    V->>C: POST /api/orders/{orderId}/payments
    C->>P: Create simulated payment
    P-->>C: providerPaymentId and checkoutUrl
    C->>DB: Save payment as PENDING
    C->>DB: Update order to PAYMENT_PENDING
    C-->>V: Payment pending with checkout URL

    U->>P: Complete simulated checkout
    P->>P: Create webhook payload
    P->>P: Sign timestamp + raw body with HMAC-SHA256
    P->>C: POST /api/webhooks/mock-payments

    C->>C: Read raw request body
    C->>C: Verify timestamp and HMAC signature
    C->>DB: Insert webhook event by provider_event_id
    DB-->>C: Event stored
    C->>Q: Publish webhook event ID
    C-->>P: 200 OK

    Q->>C: Consume webhook event
    C->>DB: Load webhook event and payment
    C->>C: Validate amount, currency, order ID, and state transition
    C->>DB: Mark payment COMPLETED
    C->>DB: Mark order PAID
    C->>DB: Mark webhook event PROCESSED
    C-->>V: Send SSE order status update
```

The Commerce API should respond to the provider quickly after the webhook is verified, stored, and queued. Business processing happens asynchronously through the queue.

## 2. Duplicate Webhook Event

```mermaid
sequenceDiagram
    autonumber
    participant P as Mock Payment Provider
    participant C as Commerce API
    participant DB as PostgreSQL

    P->>C: POST webhook event evt_123
    C->>C: Verify timestamp and signature
    C->>DB: Insert provider_event_id evt_123
    DB-->>C: Event already exists
    C->>C: Treat as duplicate
    C-->>P: 200 OK
```

A duplicate event should not be processed again. Returning `200 OK` prevents the provider from retrying an event that the Commerce API has already accepted.

## 3. Invalid Signature Or Expired Timestamp

```mermaid
sequenceDiagram
    autonumber
    participant P as Mock Payment Provider
    participant C as Commerce API
    participant DB as PostgreSQL

    P->>C: POST webhook with invalid signature or old timestamp
    C->>C: Read raw request body
    C->>C: Verify timestamp and HMAC signature
    C-->>P: 401 Unauthorized or 400 Bad Request
    Note over C,DB: Do not store or process untrusted webhook payloads.
```

The receiver must verify the signature against the exact raw request body before parsing or trusting any JSON fields.

## 4. Processing Failure And Retry

```mermaid
sequenceDiagram
    autonumber
    participant P as Mock Payment Provider
    participant C as Commerce API
    participant DB as PostgreSQL
    participant Q as RabbitMQ
    participant DLQ as Dead Letter Queue
    participant A as Admin

    P->>C: POST signed webhook
    C->>C: Verify timestamp and signature
    C->>DB: Store webhook event as RECEIVED
    C->>Q: Publish webhook event ID
    C-->>P: 200 OK

    Q->>C: Consume webhook event
    C->>DB: Mark event PROCESSING
    C->>C: Process payment event
    C->>DB: Mark event FAILED and increment attempt_count
    C->>Q: Requeue with backoff

    Q->>C: Retry webhook event
    C->>C: Processing still fails
    C->>DB: Mark event FAILED
    C->>DLQ: Move event after max attempts

    A->>C: POST /api/admin/webhook-events/{id}/retry
    C->>DB: Reset event for retry
    C->>Q: Publish webhook event ID again
```

The provider retry policy handles delivery failures. The queue retry policy handles failures after the webhook has already been accepted by the Commerce API.

## 5. Main State Changes

| Step | Payment status | Order status | Webhook event status |
|---|---|---|---|
| Order created | None | CREATED | None |
| Payment started | PENDING | PAYMENT_PENDING | None |
| Webhook accepted | PENDING | PAYMENT_PENDING | RECEIVED |
| Event processing starts | PENDING | PAYMENT_PENDING | PROCESSING |
| Payment completed | COMPLETED | PAID | PROCESSED |
| Payment failed | FAILED | PAYMENT_FAILED | PROCESSED |
| Processing error | Unchanged | Unchanged | FAILED |
| Duplicate event | Unchanged | Unchanged | IGNORED or existing status |

