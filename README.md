# Ride Hailing API

Multi-tenant, multi-region ride-hailing platform API.

## Tech Stack

- Kotlin 2.1.10 / Java 21
- Spring Boot 3.4.2
- PostgreSQL 18 (UUIDv7)
- MyBatis 3.0.3
- Redis (caching + driver locations)
- Flyway (migrations)
- Razorpay (payments)
- JWT (authentication)
- SpringDoc OpenAPI (Swagger)

## Prerequisites

- Java 21+
- PostgreSQL 18+
- Redis 7+
- Maven 3.9+

## Setup

```bash
# Create database
psql -U shikhargupta -d postgres -c "CREATE DATABASE ride_hailing;"

# Build
mvn clean package

# Run
mvn spring-boot:run
```

## Configuration

Environment variables (or `.local.env`):

| Variable | Default | Description |
|----------|---------|-------------|
| SERVER_PORT | 8080 | App port |
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | ride_hailing | Database name |
| DB_USERNAME | | DB user |
| DB_PASSWORD | | DB password |
| REDIS_HOST | localhost | Redis host |
| REDIS_PORT | 6379 | Redis port |
| RAZORPAY_KEY_ID | | Razorpay test key |
| RAZORPAY_KEY_SECRET | | Razorpay test secret |
| JWT_SECRET | (default) | JWT signing key |
| JWT_EXPIRATION_HOURS | 24 | Token expiry |

## API Endpoints

All under `/v1` context path. Auth required unless marked Public.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /v1/riders | Public | Register rider (returns JWT) |
| GET | /v1/riders/{id} | Rider | Get rider |
| POST | /v1/drivers | Public | Register driver (returns JWT) |
| GET | /v1/drivers/{id} | Driver | Get driver |
| POST | /v1/drivers/{id}/status | Driver | Go online/offline |
| POST | /v1/drivers/{id}/location | Driver | Update location |
| GET | /v1/drivers/{id}/active-ride | Driver | Get current assigned ride |
| GET | /v1/rides/available | Public | List available rides |
| POST | /v1/rides | Rider | Request a ride |
| GET | /v1/rides/{id} | Auth | Get ride status |
| POST | /v1/drivers/{id}/accept | Driver | Accept a ride |
| GET | /v1/trips/{id} | Auth | Get trip |
| GET | /v1/trips/by-ride/{rideId} | Auth | Get trip by ride |
| POST | /v1/trips/{id}/end | Driver | End trip + fare calc |
| POST | /v1/payments | Rider | Create Razorpay order |
| POST | /v1/payments/{id}/confirm | Rider | Confirm payment |
| GET | /v1/payments/{id} | Auth | Get payment |
| GET | /v1/payments/by-trip/{tripId} | Auth | Get payment by trip |

## Documentation

- Swagger UI: http://localhost:8080/v1/swagger-ui.html
- API Docs: http://localhost:8080/v1/api-docs
- Health: http://localhost:8080/v1/actuator/health
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Testing

```bash
mvn test
```

24 unit tests covering FareService, DriverService, RideService, TripService, IdempotencyUtil.

## Frontend

Separate repo: [ride-hailing-ui](https://github.com/ShikharrGuptaa/ride-hailing-ui)

- React + Vite
- Rider view: `/rider` — register, request ride, track, pay via Razorpay
- Driver view: `/driver` — register, go online, accept rides, end trips
- Real-time polling, session persistence via localStorage
