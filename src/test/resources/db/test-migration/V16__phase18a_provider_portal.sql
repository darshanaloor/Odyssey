-- Phase 18A: Provider Portal
-- Roles, provider onboarding, transport vehicles and schedules

-- 1. Add role column to users (default TRAVELLER)
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'TRAVELLER';

-- 2. Add provider-related columns to service_departures
ALTER TABLE service_departures ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SEED';
ALTER TABLE service_departures ADD COLUMN provider_id VARCHAR(64) NULL;
ALTER TABLE service_departures ADD COLUMN status_source VARCHAR(30) NULL;
ALTER TABLE service_departures ADD COLUMN schedule_id VARCHAR(64) NULL;

-- 3. Add provider-related columns to services
ALTER TABLE services ADD COLUMN provider_id VARCHAR(64) NULL;
ALTER TABLE services ADD COLUMN schedule_id VARCHAR(64) NULL;

-- 4. Add provider_id column to bookings
ALTER TABLE bookings ADD COLUMN provider_id VARCHAR(64) NULL;

-- 5. Providers table
CREATE TABLE providers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    business_name VARCHAR(150) NOT NULL,
    business_type VARCHAR(20) NOT NULL,
    contact_email VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(64) NULL,
    reviewed_at DATETIME NULL,
    status_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_providers_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Provider vehicles table
CREATE TABLE provider_vehicles (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    vehicle_number VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(100) NOT NULL,
    seats_total INT NOT NULL,
    amenities VARCHAR(200) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_vehicle_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
    UNIQUE KEY uq_provider_vehicle_number (provider_id, vehicle_number),
    INDEX idx_vehicle_provider (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Provider schedules table
CREATE TABLE provider_schedules (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    vehicle_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(64) NULL,
    origin_city VARCHAR(50) NOT NULL,
    destination_city VARCHAR(50) NOT NULL,
    origin_stop VARCHAR(255) NOT NULL,
    destination_stop VARCHAR(255) NOT NULL,
    departure_time TIME NOT NULL,
    arrival_time TIME NOT NULL,
    arrives_next_day BOOLEAN NOT NULL DEFAULT FALSE,
    price DECIMAL(10,2) NOT NULL,
    seats_offered INT NOT NULL,
    days_of_week VARCHAR(50) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    reliability INT NOT NULL DEFAULT 85,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_schedule_provider FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_vehicle FOREIGN KEY (vehicle_id) REFERENCES provider_vehicles(id) ON DELETE CASCADE,
    INDEX idx_schedule_provider (provider_id),
    INDEX idx_schedule_vehicle (vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
