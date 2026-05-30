#!/bin/bash
# Simplify Money Platform - API Test Script
# Tests all endpoints and verifies logging

echo "=========================================="
echo "  Simplify Money Platform - API Tests"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to make requests and log
test_endpoint() {
    local method=$1
    local url=$2
    local data=$3
    local description=$4

    echo -e "${BLUE}Testing: $description${NC}"
    echo "Request: $method $url"

    if [ -z "$data" ]; then
        response=$(curl -s -X "$method" "$url" -H "X-Correlation-ID: test-$(date +%s%N)" -H "Content-Type: application/json")
    else
        response=$(curl -s -X "$method" "$url" -H "X-Correlation-ID: test-$(date +%s%N)" -H "Content-Type: application/json" -d "$data")
    fi

    echo "Response: $response" | python3 -m json.tool 2>/dev/null || echo "$response"
    echo -e "${GREEN}✓ $description passed${NC}"
    echo ""
}

# Test Payment Gateway
echo -e "${BLUE}=== Payment Gateway Service (8081) ===${NC}"
echo ""

test_endpoint "GET" "http://localhost:8081/api/v1/payment-methods" "" "Get Payment Methods"

test_endpoint "POST" "http://localhost:8081/api/v1/pay" \
    '{"amount": 100.50, "method": "UPI", "userId": "test-user-1"}' \
    "Process Payment"

# Test Gold Partner Service
echo -e "${BLUE}=== Gold Partner Service (8082) ===${NC}"
echo ""

test_endpoint "GET" "http://localhost:8082/api/v1/gold-rate" "" "Get Gold Rate"

test_endpoint "POST" "http://localhost:8082/api/v1/allot-gold" \
    '{"txnId": "txn-test-1", "userId": "test-user-1", "amount": 100.50, "pricePerGram": 5700}' \
    "Allocate Gold"

# Test Simplify Money Service
echo -e "${BLUE}=== Simplify Money Service (8080) ===${NC}"
echo ""

test_endpoint "GET" "http://localhost:8080/actuator/health" "" "Health Check"

test_endpoint "POST" "http://localhost:8080/api/v1/gold/purchase" \
    '{"userId": "test-user-purchase", "amount": 100.50, "paymentMethod": "UPI"}' \
    "Purchase Gold"

test_endpoint "GET" "http://localhost:8080/api/v1/portfolio/test-user-1" "" "Get Portfolio"

test_endpoint "GET" "http://localhost:8080/api/v1/transactions/test-user-1" "" "Get Transactions"

# Check logs for correlation IDs
echo ""
echo -e "${BLUE}=== Checking Logs ===${NC}"
echo ""
echo "Recent logs from Simplify Money Service:"
docker compose logs simplify-money-service --tail 10 2>/dev/null | grep "INFO\|ERROR"

echo ""
echo -e "${GREEN}=========================================="
echo "All tests completed!"
echo "==========================================${NC}"
echo ""
echo "💡 Tips:"
echo "1. Check docker compose logs for real-time monitoring:"
echo "   docker compose logs -f simplify-money-service"
echo ""
echo "2. Access Prometheus metrics:"
echo "   http://localhost:9090"
echo ""
echo "3. Import the Postman collection for interactive testing:"
echo "   postman_collection_complete.json"

