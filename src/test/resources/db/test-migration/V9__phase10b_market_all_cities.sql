-- V9__phase10b_market_all_cities.sql
-- Add source column and uniqueness constraints for lazy deterministic marketplace materialisation

-- 1. Services: track source (SEED vs GENERATED) and enforce idempotent generation under concurrency
ALTER TABLE services ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SEED';
ALTER TABLE services ADD CONSTRAINT uq_service_route_slot UNIQUE (origin_city, destination_city, mode, departure_time, vehicle_number);

-- 2. Hotels: track source and uniqueness per city and property name
ALTER TABLE hotels ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SEED';
ALTER TABLE hotels ADD CONSTRAINT uq_hotel_city_name UNIQUE (city, name);

-- 3. Cabs: track source and uniqueness per city and provider name
ALTER TABLE cab_services ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SEED';
ALTER TABLE cab_services ADD CONSTRAINT uq_cab_city_provider UNIQUE (city, provider);

-- 4. Composite index for fast departure lookups by date
CREATE INDEX idx_departures_service_date ON service_departures (service_id, travel_date);
