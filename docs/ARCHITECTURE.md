# Ride Hailing Platform - Architecture Document

## 1. High-Level Design (HLD)

### 1.1 System Overview

A multi-tenant, multi-region ride-hailing platform supporting real-time driver-rider matching, trip lifecycle management, dynamic fare calculation, and payment processing via Razorpay.

### 1.2 Architecture Diagram

```
                    ┌─────────────┐
                    │   Frontend   │
                    │  (React/Vite)│
                    └──────┬──────┘
                           │ HTTP/REST + JWT
                    ┌──────▼──────┐
                    │  API Gateway │
                    │ (Spring Boot)│
                    └──────┬──────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
   ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
   │  PostgreSQL  │ │    Redis    │ │  Razorpay   │
   │  (Primary)   │ │  (Cache +   │ │   (PSP)     │
   │              │ │  Locations) │ │             │
   └──────────────┘ └─────────────┘ └─────────────┘
```

### 1.3 Core Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| API Server | Kotlin + Spring Boot 3.4 | REST API, business logic |
| Database | PostgreSQL 18 (UUIDv7) | Transactional data, type system |
| Cache | Redis | Driver locations, ride caching |
| Payment | Razorpay | Payment order creation, checkout |
| Frontend | React + Vite | Rider/Driver dashboards |
| Auth | JWT (HMAC-SHA256) | Stateless authentication |
| Migration | Flyway | Schema versioning |
| ORM | MyBatis | SQL mapping |

### 1.4 Multi-Tenant Architecture

- Shared database, shared schema approach
- `tenant_id` column on all entity tables
- `tenants` table with code-based lookup
- Tenant resolved via `TenantService` (extensible to header-based resolution)

### 1.5 Multi-Region Design

- `region_id` column on drivers, riders, rides, trips
- Region as a `type` entry (MUMBAI, DELHI, BANGALORE, etc.)
- Designed for region-local writes; cross-region sync deferred to infrastructure layer
- In production: separate DB per region with global routing

### 1.6 Scalability Considerations

| Requirement | Solution |
|-------------|----------|
| ~100k drivers | Redis for real-time location, DB for profile |
| ~10k rides/min | Stateless API servers, horizontal scaling |
| ~200k location updates/sec | Separate `driver_current_locations` hot table, Redis TTL cache |
| Driver matching < 1s p95 | Haversine query with spatial index, Redis geo |
| Region isolation | Region-tagged data, region-local writes |

---

## 2. Low-Level Design (LLD)

### 2.1 Database Schema

#### Type System (IdName Pattern)
```
type_group (id, name, add_date, update_date, delete_info)
type (id, group_id FK, name, add_date, update_date, delete_info)
```
Groups: Driver Status, Ride Status, Trip Status, Payment Status, Vehicle Type, Payment Method, Region

#### Entity Tables
All entity tables include: `tenant_id`, `region_id`, `add_date`, `update_date`, `delete_info` (soft delete via JSONB)

- `tenants` — multi-tenant support
- `drivers` — profile data only (no location)
- `driver_current_locations` — 1:1 hot table for real-time position (UPSERT)
- `driver_locations` — append-only location history
- `riders` — rider profiles
- `rides` — ride lifecycle (REQUESTED → DRIVER_ACCEPTED → COMPLETED)
- `trips` — trip execution with fare breakdown
- `payments` — payment records with PSP reference

#### Key Design Decisions
- UUIDv7 for all PKs (time-ordered, B-tree friendly)
- `TIMESTAMP WITH TIME ZONE` for all timestamps
- `add_date NOT NULL`, `update_date` nullable
- Partial unique index on `idempotency_key` (only active records)
- Separate hot table for driver locations (isolates write churn)

### 2.2 API Design

#### Authentication
- JWT tokens issued on registration
- `Authorization: Bearer <token>` on all authenticated endpoints
- Token contains: userId, role (RIDER/DRIVER), expiry
- Public endpoints: POST /riders, POST /drivers, GET /rides/available

#### Core APIs

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /v1/riders | Public | Register rider, returns JWT |
| POST | /v1/drivers | Public | Register driver, returns JWT |
| POST | /v1/drivers/{id}/status | Driver | Go online/offline |
| POST | /v1/drivers/{id}/location | Driver | Update location |
| GET | /v1/rides/available | Public | List REQUESTED rides |
| POST | /v1/rides | Rider | Create ride request |
| GET | /v1/rides/{id} | Auth | Get ride status |
| POST | /v1/drivers/{id}/accept | Driver | Accept a ride |
| POST | /v1/trips/{id}/end | Driver | End trip, calculate fare |
| POST | /v1/payments | Rider | Create Razorpay order |
| POST | /v1/payments/{id}/confirm | Rider | Confirm after checkout |

#### Idempotency
- Server-generated SHA-256 hash from business fields
- Rides: `SHA256(riderId + pickup + destination + vehicleType)`
- Payments: `SHA256(tripId + paymentMethodId)`
- Partial unique index: only enforced for active (non-terminal) records
- Completed/cancelled records allow re-creation with same key

### 2.3 Ride Lifecycle State Machine

```
REQUESTED (201)
    │
    ▼ [Driver accepts]
DRIVER_ACCEPTED (204)
    │
    ▼ [Trip created]
IN_PROGRESS (205)
    │
    ▼ [Trip ended]
COMPLETED (206)
```

### 2.4 Fare Calculation

```
Base Fare + (Distance × Per-KM Rate) + (Time × Per-Min Rate) × Surge Multiplier

Vehicle Rates:
  ECONOMY:  ₹30 base + ₹12/km + ₹1.5/min
  PREMIUM:  ₹50 base + ₹18/km + ₹2.5/min
  SUV:      ₹70 base + ₹22/km + ₹3.0/min

Distance: Haversine formula (great-circle distance)
Duration: Time between trip start and end
```

### 2.5 Payment Flow

```
1. Rider calls POST /payments → Backend creates Razorpay order → Returns PENDING with order_id
2. Frontend opens Razorpay checkout popup with order_id
3. Customer completes payment (card/UPI)
4. Razorpay returns razorpay_payment_id to frontend
5. Frontend calls POST /payments/{id}/confirm with payment_id
6. Backend marks payment as COMPLETED
```

### 2.6 Caching Strategy

| Data | Cache | TTL | Invalidation |
|------|-------|-----|-------------|
| Driver location | Redis | 60s | On location update |
| Driver profile | Redis | 300s | On status change |
| Ride status | Redis | 300s | On status change |

### 2.7 Driver Matching Algorithm

1. Rider creates ride with pickup coordinates and vehicle type
2. Ride stays as REQUESTED
3. Online drivers poll `GET /rides/available?vehicleTypeId=X`
4. Driver accepts → `assignDriver` with optimistic lock (`WHERE status_id = 201`)
5. First driver to accept wins; others get CONCURRENT_MODIFICATION error
6. Haversine-based nearby driver search available for future auto-matching

### 2.8 Error Handling

- `ApplicationException` with typed error codes (1-100)
- `GlobalExceptionHandler` returns consistent `ApiResponse` format
- All errors: `{ success: false, error: { code, message } }`
- Validation errors from Jakarta annotations caught and formatted

### 2.9 Project Structure

```
src/main/kotlin/com/ridehailing/
├── config/          # Redis, CORS, JWT, Exception handling, Constants
├── controller/      # REST controllers (Driver, Rider, Ride, Trip, Payment)
├── mapper/          # MyBatis mapper interfaces
├── model/
│   ├── common/      # ApiResponse, ApplicationException, IdName
│   ├── driver/      # Driver, DriverCurrentLocation, DriverLocation
│   ├── rider/       # Rider
│   ├── ride/        # Ride
│   ├── trip/        # Trip
│   ├── payment/     # Payment
│   ├── enums/       # DriverStatus, RideStatus, TripStatus, etc.
│   └── dto/         # Request DTOs
├── service/         # Business logic
│   └── payment/     # PaymentGateway interface + Razorpay impl
└── util/            # IdempotencyUtil

src/main/resources/
├── application.yml
├── db/migration/    # Flyway (V1 schema, V2 partial unique index)
└── mapper/          # MyBatis XML mappers
```

### 2.10 Security

- JWT authentication (HMAC-SHA256, 24h expiry)
- Input validation via Jakarta Bean Validation
- Phone number regex validation (Indian mobile)
- Email format validation
- Type ID validation against enums
- Soft delete (no hard deletes)
- CORS restricted to frontend origins
- Idempotency prevents duplicate operations


### 2.11 API Documentation

- Swagger UI: `http://localhost:8080/v1/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v1/api-docs`
- All endpoints annotated with `@Operation` for auto-generated docs
- SpringDoc OpenAPI 2.8.4

### 2.12 Health & Monitoring

- Health check: `http://localhost:8080/v1/actuator/health`
- Metrics: `http://localhost:8080/v1/actuator/metrics`
- Spring Actuator with health, info, metrics endpoints exposed