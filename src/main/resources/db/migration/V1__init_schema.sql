-- Type group and type tables (IdName pattern)
CREATE TABLE type_group (
  id INTEGER PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

CREATE TABLE type (
  id INTEGER PRIMARY KEY,
  group_id INTEGER NOT NULL REFERENCES type_group(id),
  name VARCHAR(100) NOT NULL,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

CREATE INDEX idx_type_group_id ON type(group_id);

-- Seed type groups
INSERT INTO type_group (id, name, add_date) VALUES
  (1, 'Driver Status', NOW()),
  (2, 'Ride Status', NOW()),
  (3, 'Trip Status', NOW()),
  (4, 'Payment Status', NOW()),
  (5, 'Vehicle Type', NOW()),
  (6, 'Payment Method', NOW()),
  (7, 'Region', NOW());

-- Driver Status (group 1)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (101, 1, 'ONLINE', NOW()),
  (102, 1, 'OFFLINE', NOW()),
  (103, 1, 'ON_TRIP', NOW());

-- Ride Status (group 2)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (201, 2, 'REQUESTED', NOW()),
  (202, 2, 'MATCHING', NOW()),
  (203, 2, 'DRIVER_ASSIGNED', NOW()),
  (204, 2, 'DRIVER_ACCEPTED', NOW()),
  (205, 2, 'IN_PROGRESS', NOW()),
  (206, 2, 'COMPLETED', NOW()),
  (207, 2, 'CANCELLED', NOW());

-- Trip Status (group 3)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (301, 3, 'IN_PROGRESS', NOW()),
  (302, 3, 'PAUSED', NOW()),
  (303, 3, 'COMPLETED', NOW());

-- Payment Status (group 4)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (401, 4, 'PENDING', NOW()),
  (402, 4, 'PROCESSING', NOW()),
  (403, 4, 'COMPLETED', NOW()),
  (404, 4, 'FAILED', NOW()),
  (405, 4, 'REFUNDED', NOW());

-- Vehicle Type (group 5)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (501, 5, 'ECONOMY', NOW()),
  (502, 5, 'PREMIUM', NOW()),
  (503, 5, 'SUV', NOW());

-- Payment Method (group 6)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (601, 6, 'UPI', NOW()),
  (602, 6, 'CARD', NOW()),
  (603, 6, 'WALLET', NOW()),
  (604, 6, 'CASH', NOW());

-- Region (group 7)
INSERT INTO type (id, group_id, name, add_date) VALUES
  (701, 7, 'MUMBAI', NOW()),
  (702, 7, 'DELHI', NOW()),
  (703, 7, 'BANGALORE', NOW()),
  (704, 7, 'HYDERABAD', NOW()),
  (705, 7, 'CHENNAI', NOW());

-- Tenants table (multi-tenant support)
CREATE TABLE tenants (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  name VARCHAR(255) NOT NULL,
  code VARCHAR(50) NOT NULL UNIQUE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

INSERT INTO tenants (id, name, code, add_date) VALUES
  (uuidv7(), 'Default', 'DEFAULT', NOW());

-- Drivers table
CREATE TABLE drivers (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  region_id INTEGER REFERENCES type(id),
  name VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  vehicle_type_id INTEGER NOT NULL REFERENCES type(id),
  license_plate VARCHAR(20) NOT NULL,
  status_id INTEGER NOT NULL DEFAULT 102 REFERENCES type(id),
  rating NUMERIC(3,2) DEFAULT 5.00,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

CREATE UNIQUE INDEX idx_drivers_phone_tenant ON drivers(phone, tenant_id) WHERE delete_info IS NULL;

-- Driver current location (1:1 hot table)
CREATE TABLE driver_current_locations (
  driver_id UUID PRIMARY KEY REFERENCES drivers(id),
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE
);

-- Riders table
CREATE TABLE riders (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  region_id INTEGER REFERENCES type(id),
  name VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  email VARCHAR(255),
  rating NUMERIC(3,2) DEFAULT 5.00,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

CREATE UNIQUE INDEX idx_riders_phone_tenant ON riders(phone, tenant_id) WHERE delete_info IS NULL;

-- Rides table
CREATE TABLE rides (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  region_id INTEGER REFERENCES type(id),
  rider_id UUID NOT NULL REFERENCES riders(id),
  driver_id UUID REFERENCES drivers(id),
  status_id INTEGER NOT NULL DEFAULT 201 REFERENCES type(id),
  pickup_lat DOUBLE PRECISION NOT NULL,
  pickup_lng DOUBLE PRECISION NOT NULL,
  pickup_address VARCHAR(500),
  destination_lat DOUBLE PRECISION NOT NULL,
  destination_lng DOUBLE PRECISION NOT NULL,
  destination_address VARCHAR(500),
  vehicle_type_id INTEGER NOT NULL DEFAULT 501 REFERENCES type(id),
  estimated_fare NUMERIC(10,2),
  surge_multiplier NUMERIC(4,2) DEFAULT 1.00,
  idempotency_key VARCHAR(100) UNIQUE,
  requested_at TIMESTAMP WITH TIME ZONE,
  matched_at TIMESTAMP WITH TIME ZONE,
  started_at TIMESTAMP WITH TIME ZONE,
  completed_at TIMESTAMP WITH TIME ZONE,
  cancelled_at TIMESTAMP WITH TIME ZONE,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

-- Trips table
CREATE TABLE trips (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  region_id INTEGER REFERENCES type(id),
  ride_id UUID NOT NULL UNIQUE REFERENCES rides(id),
  driver_id UUID NOT NULL REFERENCES drivers(id),
  rider_id UUID NOT NULL REFERENCES riders(id),
  status_id INTEGER NOT NULL DEFAULT 301 REFERENCES type(id),
  start_lat DOUBLE PRECISION NOT NULL,
  start_lng DOUBLE PRECISION NOT NULL,
  end_lat DOUBLE PRECISION,
  end_lng DOUBLE PRECISION,
  distance_km NUMERIC(10,3),
  duration_minutes NUMERIC(10,2),
  base_fare NUMERIC(10,2),
  distance_fare NUMERIC(10,2),
  time_fare NUMERIC(10,2),
  surge_multiplier NUMERIC(4,2) DEFAULT 1.00,
  total_fare NUMERIC(10,2),
  started_at TIMESTAMP WITH TIME ZONE,
  ended_at TIMESTAMP WITH TIME ZONE,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

-- Payments table
CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT uuidv7(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  trip_id UUID NOT NULL REFERENCES trips(id),
  rider_id UUID NOT NULL REFERENCES riders(id),
  amount NUMERIC(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  status_id INTEGER NOT NULL DEFAULT 401 REFERENCES type(id),
  payment_method_id INTEGER NOT NULL REFERENCES type(id),
  psp_reference VARCHAR(255),
  idempotency_key VARCHAR(100) UNIQUE,
  add_date TIMESTAMP WITH TIME ZONE NOT NULL,
  update_date TIMESTAMP WITH TIME ZONE,
  delete_info JSONB
);

-- Driver location history (append-only)
CREATE TABLE driver_locations (
  id BIGSERIAL PRIMARY KEY,
  driver_id UUID NOT NULL REFERENCES drivers(id),
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes
CREATE INDEX idx_rides_rider_id ON rides(rider_id);
CREATE INDEX idx_rides_driver_id ON rides(driver_id);
CREATE INDEX idx_rides_status_id ON rides(status_id);
CREATE INDEX idx_rides_tenant_id ON rides(tenant_id);
CREATE INDEX idx_rides_region_id ON rides(region_id);
CREATE INDEX idx_rides_idempotency_key ON rides(idempotency_key);
CREATE INDEX idx_trips_ride_id ON trips(ride_id);
CREATE INDEX idx_trips_status_id ON trips(status_id);
CREATE INDEX idx_trips_tenant_id ON trips(tenant_id);
CREATE INDEX idx_payments_trip_id ON payments(trip_id);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_drivers_status_id ON drivers(status_id);
CREATE INDEX idx_drivers_vehicle_type_id ON drivers(vehicle_type_id);
CREATE INDEX idx_drivers_tenant_id ON drivers(tenant_id);
CREATE INDEX idx_drivers_region_id ON drivers(region_id);
CREATE INDEX idx_riders_tenant_id ON riders(tenant_id);
CREATE INDEX idx_driver_locations_driver_id ON driver_locations(driver_id);
CREATE INDEX idx_driver_locations_recorded_at ON driver_locations(recorded_at);
CREATE INDEX idx_driver_current_locations_lat_lng ON driver_current_locations(lat, lng);
