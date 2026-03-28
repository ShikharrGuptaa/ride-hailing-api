# Ride Hailing API

Multi-tenant, multi-region ride-hailing platform API built with Kotlin + Spring Boot.

## Tech Stack

- Kotlin 2.1.10 / Java 21
- Spring Boot 3.4.2
- PostgreSQL 18 (UUIDv7 native)
- MyBatis 3.0.3
- Redis (caching + real-time driver locations)
- Flyway (database migrations)
- SpringDoc OpenAPI (Swagger UI)

## Prerequisites

- Java 21+
- PostgreSQL 18+
- Redis 7+
- Maven 3.9+

## Database Setup

```bash
# Create the database
psql -U postgres -c "CREATE DATABASE ride_hailing;"
```

Flyway will auto-run migrations on startup.

## Configuration

Environment variables (or use `.env` file):

| Variable | Default | Description |
|----------|---------|-------------|
| SERVER_PORT | 8080 | Application port |
| DB_HOST | localhost | PostgreSQL host |
| DB_PORT | 5432 | PostgreSQL port |
| DB_NAME | ride_hailing | Database name |
| DB_USERNAME | postgres | Database user |
| DB_PASSWORD | postgres | Database password |
| REDIS_HOST | localhost | Redis host |
| REDIS_PORT | 6379 | Redis port |

## Build & Run

```bash
# Build
mvn clean package

# Run
java -jar target/ride-hailing-api-1.0.0-SNAPSHOT.jar

# Or run with Maven
mvn spring-boot:run
```

## API Endpoints

All endpoints are under `/v1` context path.

### Core APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /v1/rides | Create a ride request |
| GET | /v1/rides/{id} | Get ride status |
| POST | /v1/drivers/{id}/location | Update driver location |
| POST | /v1/drivers/{id}/accept | Accept ride assignment |
| POST | /v1/trips/{id}/end | End trip + fare calculation |
| POST | /v1/payments | Trigger payment flow |

### Seed Data (non-prod only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /v1/seed/drivers | Seed test drivers |
| POST | /v1/seed/riders | Seed test riders |

### Docs & Health

- Swagger UI: http://localhost:8080/v1/swagger-ui.html
- API Docs: http://localhost:8080/v1/api-docs
- Health: http://localhost:8080/v1/actuator/health

## Database Architecture

- `type_group` / `type` — IdName lookup pattern for all statuses and enums
- `tenants` — Multi-tenant support
- `drivers` — Driver profiles (static data)
- `driver_current_locations` — Hot 1:1 table for real-time driver position
- `driver_locations` — Append-only location history
- `riders` — Rider profiles
- `rides` — Ride lifecycle (request → match → accept → complete)
- `trips` — Trip execution (start → end, fare calculation)
- `payments` — Payment processing with PSP integration

All entity tables include `tenant_id`, `region_id`, `add_date`, `update_date`, and `delete_info` (soft delete via JSONB).

UUIDv7 used for all primary keys (time-ordered, B-tree friendly).

## Project Structure

```
src/main/kotlin/com/ridehailing/
├── RideHailingApplication.kt
├── config/          # Redis, WebSocket, exception handling
├── controller/      # REST controllers
├── mapper/          # MyBatis mapper interfaces
├── model/           # Entities, DTOs, enums, IdName
└── service/         # Business logic

src/main/resources/
├── application.yml
├── db/migration/    # Flyway SQL migrations
└── mapper/          # MyBatis XML mappers
```