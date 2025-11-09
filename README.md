# Payment Gateway Challenge

## Overview

This project implements a lightweight **Payment Gateway API** that lets merchants process and retrieve card payments through a simulated bank.  
It validates payment data, calls the bank simulator, stores results in memory, and returns one of three outcomes: **Authorized**, **Declined**, or **Rejected**.

The goal was to meet the functional requirements cleanly and clearly, without unnecessary complexity or over-engineering.

---

## What I Did

- Implemented a clear **controller → service → repository** structure.
- Created **BankRequest** and **BankResponse** DTOs to handle the payload shapes from the bank simulator, keeping gateway logic independent and flexible.
- Made the **service layer modular**, with separate functions for each validation and a single orchestrator method to keep the flow clean.
- Added **logging** at key points to trace flow and outcomes, while avoiding sensitive data such as full card numbers or CVVs.
- Implemented **comprehensive tests** covering:
  - Happy paths (Authorized, Declined)
  - Invalid inputs (Rejected)
  - Edge cases like lowercase currency
  - Ensuring invalid requests never hit the simulator to avoid unnecessary calls.

---

## Why I Designed It This Way

- **Modularity:** Each component handles one responsibility, which makes testing and maintenance straightforward.
- **Separation of concerns:** The controller remains lean, the service owns validation and external calls, and the repository handles storage.
- **Realistic flow:** Validation happens before any external call, just like in a real payment gateway to prevent cost-incurring requests.
- **Bank abstraction:** DTOs isolate the bank-specific contract so the gateway can evolve independently if the acquiring API changes later.

---

## Example API Requests

**Authorized (odd-ending PAN)**
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,"currency":"USD","amount":1050,"cvv":123}' | jq
```

### Declined (even-ending PAN)
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111111111111112","expiry_month":12,"expiry_year":2029,"currency":"USD","amount":1050,"cvv":123}' | jq
 ```

### Bank Error (zero-ending PAN, simulator returns 503)
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111111111111110","expiry_month":12,"expiry_year":2029,"currency":"USD","amount":1050,"cvv":123}' | jq
```

### Rejected (invalid format)
```bash
curl -i -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111-1111-1111-1111","expiry_month":12,"expiry_year":2029,"currency":"USD","amount":1050,"cvv":123}'
```

### Authorized with lowercase currency
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,"currency":"usd","amount":1050,"cvv":123}' | jq
```
---

## Tests

The test suite covers:
- Authorized, Declined, and Rejected payment flows
- Lowercase currency validation
- Bank 503 (Declined)
- Retrieving existing and missing payments
- Ensuring invalid requests never hit the simulator

All tests pass using:
```bash
./gradlew test
```

## Future Improvements

If this were a production system, I would:
- Store card data securely (hashed or encrypted) and ensure sensitive fields are never logged
- Replace the in-memory repository with a real database for persistence and reliability
- Add observability through metrics, tracing, and structured logs for easier debugging and monitoring
- Improve scalability and resilience with concurrency-safe operations, timeouts, and retry mechanisms

---
## API Documentation
Swagger UI is included for interactive testing and documentation.
Once the application is running, open:

http://localhost:8090/swagger-ui/index.html

## Summary

This implementation fulfills all functional requirements from the brief.  
The design is simple, modular, and production-minded, with clear validation, test coverage, and realistic request handling.

