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
- Improved **validation** logic to support realistic formats (e.g. "01" expiry months).
- Added structured JSON response for **rejected** requests for consistency.
- Ensured **last four digits** are stored as **strings** to preserve leading zeros.
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
> Note: `expiry_month` accepts numeric values (1–12). When testing via JSON, avoid leading-zero numbers (e.g. `01`) as they’re invalid JSON.  
> Use plain numbers like `1`, or wrap with quotes (`"01"`) if you prefer the card-style format — both are accepted by the API.

### POST Requests (Process Payment)
The following examples demonstrate different outcomes based on the card number used.

**Authorized (odd-ending PAN)**
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111222233334445","expiry_month":12,"expiry_year":2029,"currency":"GBP","amount":1050,"cvv":1243}' | jq
```

### Declined (even-ending PAN)
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111222233334444","expiry_month":"01","expiry_year":2030,"currency":"EUR","amount":10590,"cvv":133}' | jq
 ```

### Bank Error (zero-ending PAN, simulator returns 503)
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111432167890122","expiry_month":"06","expiry_year":2027,"currency":"USD","amount":10850,"cvv":124}' | jq
```

### Rejected (invalid format)
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111-5555-6666-7777 ","expiry_month":12,"expiry_year":2026,"currency":"EUR","amount":12050,"cvv":113}' | jq
```

### Authorized with lowercase currency
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111987654321123","expiry_month":12,"expiry_year":2029,"currency":"gbp","amount":10450,"cvv":1293}' | jq
```
---
### Retrieve Payment by ID

Payments can be retrieved during runtime using their unique identifier.  
This returns status, masked card number (last four digits only), expiry, currency, and amount.

```bash
curl -s http://localhost:8090/api/payments/<id> | jq
```
**Example:**
```bash
curl -s http://localhost:8090/api/payments/ca2be3df-1039-412a-aadb-41219b3ebf05 | jq
```
> **Note:** IDs are stored in-memory and reset on each run. Replace `<id>` with the one returned in your POST response.

### Full Flow Example (Create + Retrieve)

```bash
# Create a payment
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111678943210987","expiry_month":"09","expiry_year":2029,"currency":"USD","amount":10950,"cvv":183}' | jq

# Retrieve it using the ID from the response
curl -s http://localhost:8090/api/payments/<id> | jq

# Or run the full flow in one command (create + retrieve)
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111678943210987","expiry_month":"09","expiry_year":2029,"currency":"USD","amount":10950,"cvv":183}' \
 | tee >(jq) | jq -r '.id' | xargs -I {} curl -s http://localhost:8090/api/payments/{} | jq
```
---
### Example API Responses

**POST /api/payments**

Example Request:
```bash
curl -s -X POST http://localhost:8090/api/payments \
 -H "Content-Type: application/json" \
 -d '{"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,"currency":"USD","amount":1050,"cvv":123}' | jq
```
Example Response:
```json
{
  "id": "683930a6-307a-4171-ad9f-1409a666003d",
  "status": "Authorized",
  "cardNumberLastFour": 1111,
  "expiryMonth": 12,
  "expiryYear": 2029,
  "currency": "USD",
  "amount": 1050
}
```
---

**GET /api/payments/{id}**

Example Request:
```bash
curl -s http://localhost:8090/api/payments/683930a6-307a-4171-ad9f-1409a666003d | jq
```

Example Response:
```json
{
  "id": "683930a6-307a-4171-ad9f-1409a666003d",
  "status": "Authorized",
  "cardNumberLastFour": 1111,
  "expiryMonth": 12,
  "expiryYear": 2029,
  "currency": "USD",
  "amount": 1050
}
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

