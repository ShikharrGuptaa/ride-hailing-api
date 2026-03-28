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
| GET | /v1/riders/{id} | Rider | Get rider details |
| POST | /v1/drivers | Public | Register driver (returns JWT) |
| GET | /v1/drivers/{id} | Driver | Get driver details |
| POST | /v1/drivers/{id}/status | Driver | Go online/offline |
| POST | /v1/drivers/{id}/location | Driver | Update location |
| GET | /v1/drivers/{id}/active-ride | Driver | Get current assigned ride |
| GET | /v1/rides/available | Public | List available rides (filter by vehicleTypeId) |
| GET | /v1/rides/estimate | Public | Estimate fare for a route |
| POST | /v1/rides | Rider | Request a ride |
| GET | /v1/rides/{id} | Auth | Get ride status |
| POST | /v1/drivers/{id}/accept | Driver | Accept a ride |
| GET | /v1/trips/{id} | Auth | Get trip details |
| GET | /v1/trips/by-ride/{rideId} | Auth | Get trip by ride ID |
| POST | /v1/trips/{id}/end | Driver | End trip + calculate fare |
| POST | /v1/payments | Rider | Create Razorpay order |
| GET | /v1/payments/{id} | Auth | Get payment details |
| GET | /v1/payments/by-trip/{tripId} | Auth | Get payment by trip ID |
| POST | /v1/payments/{id}/confirm | Rider | Confirm payment |

### WebSocket Endpoints

Connect to `/ws` (with SockJS fallback) for real-time updates:

| Topic | Description |
|-------|-------------|
| /topic/rides/available | New ride requests broadcast to all drivers |
| /topic/rides/{rideId} | Ride status updates for specific ride |
| /topic/drivers/{driverId}/rides | Driver-specific ride assignments |

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

## Distance Calculation

The system uses the **Haversine formula** to calculate great-circle distance between GPS coordinates:

- **Formula**: Uses Earth's radius (6371 km) for spherical distance calculation
- **Accuracy**: Suitable for short distances and fare estimation
- **Trade-off**: Calculates straight-line distance, not actual road routes
- **Performance**: Fast, no external API calls, works offline

For production systems requiring road-based routing, consider integrating:
- Google Maps Distance Matrix API
- Mapbox Directions API
- OSRM (Open Source Routing Machine)

## Key Features

- **Multi-tenant & Multi-region**: Tenant-based isolation, region-tagged data
- **Real-time Updates**: WebSocket (STOMP/SockJS) for live ride notifications
- **Haversine Distance**: Great-circle distance calculation for fare estimation
- **Idempotency**: SHA-256 hash-based duplicate prevention
- **Optimistic Locking**: First-driver-wins ride acceptance
- **Soft Deletes**: JSONB-based delete_info for audit trail
- **UUIDv7**: Time-ordered primary keys for better B-tree performance

## Frontend

Separate repo: [ride-hailing-ui](https://github.com/ShikharrGuptaa/ride-hailing-ui)

- React + Vite
- Rider view: `/rider` — register, request ride, track, pay via Razorpay
- Driver view: `/driver` — register, go online, accept rides, end trips
- Real-time WebSocket updates, session persistence via localStorage
