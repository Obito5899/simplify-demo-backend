# Simplify Money Platform - API Testing Guide

## 🚀 Project Overview

The Simplify Money Platform is a microservices-based payment and gold investment system with three main services:

1. **Simplify Money Service** (Port 8080) - Main orchestration service
2. **Payment Gateway Service** (Port 8081) - Payment processing
3. **Gold Partner Service** (Port 8082) - Gold allocation and pricing
4. **MongoDB** (Port 27017) - Data persistence
5. **Prometheus** (Port 9090) - Metrics and monitoring

---

## 📊 Service Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Client (Postman)                         │
└────────┬────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│         Simplify Money Service (8080)                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Controllers:                                        │   │
│  │  - PurchaseController (/api/v1/gold/purchase)      │   │
│  │  - PortfolioController (/api/v1/portfolio/{id})    │   │
│  │  - TransactionsController (/api/v1/transactions)   │   │
│  └──────────────────────────────────────────────────────┘   │
│         │                              │                    │
│         ▼                              ▼                    │
│  ┌───────────────────┐        ┌──────────────────┐         │
│  │ MongoDB (27017)   │        │ Orchestration    │         │
│  │ - Transactions    │        │ Service          │         │
│  │ - Portfolios      │        │ (Retry Logic)    │         │
│  └───────────────────┘        └──────────────────┘         │
│                                      │                      │
└──────────────────────────────────────┼──────────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────┐
          ▼                            ▼                        ▼
    ┌──────────────┐          ┌──────────────────┐       ┌─────────────┐
    │  Payment     │          │   Gold Partner   │       │ Prometheus  │
    │  Gateway     │          │   Service (8082) │       │  (9090)     │
    │  (8081)      │          │                  │       │             │
    │              │          │ - Rate endpoint  │       │ Metrics &   │
    │ - Pay        │          │ - Allot endpoint │       │ Monitoring  │
    │ - Refund     │          │ - 10% failure    │       │             │
    │ - Status     │          │   rate           │       │             │
    └──────────────┘          └──────────────────┘       └─────────────┘
```

---

## 🔍 Logging Configuration

### Logging Pattern
All services use the following log pattern:
```
%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [CID: %X{correlationId}] - %msg %n
```

Example log output:
```
2026-05-25 17:07:34 [http-nio-8080-exec-1] INFO o.e.s.SimplifyMoneyServiceApplication [CID: f8a3c1e2-9b4d-4f2c-8e1a-7d6c5b4a3f2e] - Started SimplifyMoneyServiceApplication in 15.224 seconds
```

### Correlation ID Tracking
- **Header**: `X-Correlation-ID`
- **MDC Key**: `correlationId`
- **Filter**: `CorrelationIdFilter` automatically:
  - Extracts correlation ID from request header
  - Generates UUID if not provided
  - Sets in MDC for all log statements
  - Returns correlation ID in response header

### Viewing Logs
```bash
# Real-time logs for all services
docker compose logs -f

# Specific service logs
docker compose logs simplify-money-service
docker compose logs payment-gateway-service
docker compose logs gold-partner-service

# Last 50 lines
docker compose logs --tail=50 simplify-money-service

# Filter by pattern
docker compose logs simplify-money-service | grep "ERROR"
```

---

## 📮 Postman Collection

### Import Instructions
1. Open Postman
2. Click **Import** → **Upload Files**
3. Select `postman_collection_complete.json`
4. All requests are ready to use!

### Collection Structure

#### 1. **Simplify Money Service** (Main API)
- ✅ `POST /api/v1/gold/purchase` - Initiate gold purchase
- ✅ `GET /api/v1/portfolio/{userId}` - Get user portfolio
- ✅ `GET /api/v1/transactions/{userId}` - Get user transactions
- ✅ `GET /actuator/health` - Health check
- ✅ `GET /actuator/metrics` - System metrics

#### 2. **Payment Gateway Service**
- ✅ `GET /api/v1/payment-methods` - List payment methods
- ✅ `POST /api/v1/pay` - Process payment
- ✅ `GET /api/v1/payment-status/{txnId}` - Check payment status
- ✅ `POST /api/v1/refund` - Process refund

#### 3. **Gold Partner Service**
- ✅ `GET /api/v1/gold-rate` - Get current gold rate
- ✅ `POST /api/v1/allot-gold` - Allocate gold

#### 4. **Prometheus Monitoring**
- ✅ `GET /metrics` - Prometheus metrics page
- ✅ `GET /actuator/prometheus` - Service metrics
- ✅ `GET /` - Prometheus UI (open in browser)

#### 5. **Integration Tests**
- ✅ Complete Purchase Flow
- ✅ Stress Test (Run multiple times)
- ✅ Check Results

---

## 🧪 Testing Guide

### 1. Basic API Test - Get Payment Methods
```bash
curl -X GET http://localhost:8081/api/v1/payment-methods
```

**Response:**
```json
{
  "methods": ["UPI", "NET_BANKING", "CARD"]
}
```

### 2. Get Gold Rate
```bash
curl -X GET http://localhost:8082/api/v1/gold-rate
```

**Response:**
```json
{
  "pricePerGram": 5850.25,
  "timestamp": 1779728400000
}
```

### 3. Process Payment
```bash
curl -X POST http://localhost:8081/api/v1/pay \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.50,
    "method": "UPI",
    "userId": "user-1"
  }'
```

**Response (80% success rate):**
```json
{
  "txnId": "550e8400-e29b-41d4-a716-446655440000",
  "success": true,
  "message": "Payment processed"
}
```

### 4. Allocate Gold
```bash
curl -X POST http://localhost:8082/api/v1/allot-gold \
  -H "Content-Type: application/json" \
  -d '{
    "txnId": "txn-123",
    "userId": "user-1",
    "amount": 100.50,
    "pricePerGram": 5700.00
  }'
```

**Response (90% success rate):**
```json
{
  "success": true,
  "grams": 0.017632,
  "message": "Allocated",
  "txnId": "txn-123"
}
```

### 5. Complete Purchase Flow
```bash
curl -X POST http://localhost:8080/api/v1/gold/purchase \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-1" \
  -H "X-Correlation-ID: corr-123456" \
  -d '{
    "userId": "user-integration-test",
    "amount": 500.00,
    "paymentMethod": "NET_BANKING"
  }'
```

**Response:**
```json
{
  "success": true,
  "data": {
    "transactionId": "507f191e810c19729de860ea",
    "amount": 500.00,
    "state": "COMPLETED",
    "correlationId": "corr-123456"
  },
  "error": null,
  "correlationId": "corr-123456"
}
```

### 6. Get Portfolio
```bash
curl -X GET http://localhost:8080/api/v1/portfolio/user-1 \
  -H "X-Correlation-ID: corr-789012"
```

**Response:**
```json
{
  "success": true,
  "data": {
    "_id": "user-1",
    "totalInvestment": 500.00,
    "totalGrams": 0.087654,
    "createdAt": "2026-05-25T17:07:31.494Z",
    "updatedAt": "2026-05-25T17:07:32.500Z"
  },
  "error": null,
  "correlationId": "corr-789012"
}
```

### 7. Get Transactions
```bash
curl -X GET http://localhost:8080/api/v1/transactions/user-1 \
  -H "X-Correlation-ID: corr-345678"
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "_id": "507f191e810c19729de860ea",
      "userId": "user-1",
      "amount": 500.00,
      "paymentMethod": "NET_BANKING",
      "state": "COMPLETED",
      "correlationId": "corr-123456",
      "idempotencyKey": "demo-key-1",
      "createdAt": "2026-05-25T17:07:31.494Z"
    }
  ],
  "error": null,
  "correlationId": "corr-345678"
}
```

---

## ✅ Logging Verification

### Check if Logging is Working

1. **Make a request with Correlation ID:**
```bash
curl -X GET http://localhost:8080/api/v1/portfolio/test-user \
  -H "X-Correlation-ID: test-corr-12345"
```

2. **View logs with correlation ID:**
```bash
docker compose logs simplify-money-service --tail 20 | grep "test-corr-12345"
```

3. **Expected output:**
```
2026-05-25 17:10:45 [http-nio-8080-exec-2] INFO o.e.s.controller.PortfolioController [CID: test-corr-12345] - Portfolio request for user test-user
```

### Verify Logging Components

✅ **Correlation ID Filter** - Running in `simplify-money-service`
```
Location: /simplify-money-service/src/main/java/org/example/simplify/filter/CorrelationIdFilter.java
```

✅ **Logging Pattern** - Configured in all services
```
application.yml (simplify-money-service)
application.properties (payment-gateway-service)
application.properties (gold-partner-service)
Pattern: %d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [CID: %X{correlationId}] - %msg %n
```

✅ **Logger Usage** - Active in all controllers
```
Controllers using SLF4J:
- PurchaseController
- PaymentController
- GoldController
```

---

## 🔧 Environment Variables

### Payment Gateway Service
- `PAYMENT_SUCCESS_RATE` (default: 0.8) - 80% success rate for payments

### Gold Partner Service
- `GOLD_BASE_PRICE` (default: 6000) - Base price per gram in Rs.
- `GOLD_FAILURE_RATE` (default: 0.1) - 10% failure rate for allocations

### Example Docker Compose Override
```yaml
payment-gateway-service:
  environment:
    - PAYMENT_SUCCESS_RATE=0.95

gold-partner-service:
  environment:
    - GOLD_BASE_PRICE=7000
    - GOLD_FAILURE_RATE=0.05
```

---

## 📊 Monitoring

### Access Prometheus
- **URL**: http://localhost:9090
- **Metrics Scrape Interval**: 15s
- **Retention**: 15 days

### Available Metrics
```
http_requests_total - Total HTTP requests
http_request_duration_seconds - Request latency
process_cpu_usage - CPU usage
process_memory_usage - Memory usage
jvm_memory_usage_bytes - JVM memory metrics
mongodb_driver_pool_checkedout_connections - MongoDB connections
```

### Example Queries in Prometheus
```
# Request rate
rate(http_requests_total[5m])

# P95 Latency
histogram_quantile(0.95, http_request_duration_seconds_bucket)

# Error rate
rate(http_requests_total{status=~"5.."}[5m])
```

---

## 🐞 Troubleshooting

### Issue: Correlation ID not showing in logs
**Solution**: Ensure `X-Correlation-ID` header is sent in the request:
```bash
curl -X GET http://localhost:8080/api/v1/portfolio/test \
  -H "X-Correlation-ID: my-correlation-id"
```

### Issue: Services not responding
**Solution**: Check service status:
```bash
docker compose ps
docker compose logs simplify-money-service
```

### Issue: MongoDB connection error
**Solution**: Verify MongoDB is running:
```bash
docker compose logs simplify-mongo | tail 20
```

### Issue: Port already in use
**Solution**: Stop all containers and restart:
```bash
docker compose down
docker compose up --build
```

---

## 📋 Quick Reference

| Service | Port | Purpose |
|---------|------|---------|
| Simplify Money Service | 8080 | Main API + Orchestration |
| Payment Gateway | 8081 | Payment Processing |
| Gold Partner | 8082 | Gold Management |
| MongoDB | 27017 | Database |
| Prometheus | 9090 | Metrics & Monitoring |

---

## 🎯 Key Features

✨ **Microservices Architecture** - Independently scalable services  
🔗 **Service Integration** - Seamless inter-service communication  
📊 **Distributed Tracing** - Correlation IDs across services  
📈 **Metrics & Monitoring** - Prometheus integration  
🔐 **Idempotent Operations** - Idempotency keys for safe retries  
🔄 **Retry Logic** - Spring Retry with exponential backoff  
✅ **Structured Logging** - MDC-based correlation tracking  
🛡️ **Data Persistence** - MongoDB for reliable storage  

---

## 📝 Notes

- All requests should include `X-Correlation-ID` header for proper logging/tracing
- Use `Idempotency-Key` for purchase requests to ensure exactly-once semantics
- Payment success rate is 80% - retry logic handles failures
- Gold allocation has 10% failure rate - handled by orchestration service
- Correlation IDs are automatically generated if not provided

---

## 🚀 Next Steps

1. ✅ Import the Postman collection
2. ✅ Run the test requests in order
3. ✅ Monitor logs in real-time: `docker compose logs -f`
4. ✅ Check Prometheus metrics: http://localhost:9090
5. ✅ Verify correlation IDs in logs
6. ✅ Test failure scenarios (payment failures, allocation failures)

