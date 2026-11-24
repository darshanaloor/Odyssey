-- V8__phase10a_marketplace_catalogue_and_bookings.sql
-- In-app booking marketplace catalogue, departures, seat holds, hotels, cabs, and booking linkage

-- 1. Operators
CREATE TABLE operators (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mode VARCHAR(50) NOT NULL,
    contact VARCHAR(100) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Services (transport catalogue)
CREATE TABLE services (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    operator_id VARCHAR(64) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    vehicle_number VARCHAR(100) NOT NULL,
    origin_city VARCHAR(50) NOT NULL,
    destination_city VARCHAR(50) NOT NULL,
    origin_stop VARCHAR(255) NOT NULL,
    destination_stop VARCHAR(255) NOT NULL,
    departure_time TIME NOT NULL,
    arrival_time TIME NOT NULL,
    arrives_next_day BOOLEAN NOT NULL DEFAULT FALSE,
    price DECIMAL(10,2) NOT NULL,
    seats_total INT NOT NULL,
    reliability_score INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_services_operator FOREIGN KEY (operator_id) REFERENCES operators(id) ON DELETE CASCADE,
    INDEX idx_services_route (origin_city, destination_city, mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Service Departures (seats tracked per date)
CREATE TABLE service_departures (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    service_id VARCHAR(64) NOT NULL,
    travel_date DATE NOT NULL,
    seats_available INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    delay_minutes INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_departures_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    UNIQUE KEY uq_service_travel_date (service_id, travel_date),
    INDEX idx_departures_date (travel_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Seat Holds
CREATE TABLE seat_holds (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    departure_id VARCHAR(64) NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    booking_id VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_departure_seat UNIQUE (departure_id, seat_number),
    CONSTRAINT fk_seat_holds_departure FOREIGN KEY (departure_id) REFERENCES service_departures(id) ON DELETE CASCADE,
    INDEX idx_seat_holds_booking (booking_id),
    INDEX idx_seat_holds_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Hotels
CREATE TABLE hotels (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL,
    rating DECIMAL(3,1) NOT NULL,
    rooms_total INT NOT NULL,
    contact_email VARCHAR(100) NULL,
    INDEX idx_hotels_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Cab Services
CREATE TABLE cab_services (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    city VARCHAR(50) NOT NULL,
    base_fare DECIMAL(10,2) NOT NULL,
    per_km DECIMAL(10,2) NOT NULL,
    INDEX idx_cabs_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Modify bookings table for marketplace support
ALTER TABLE bookings MODIFY COLUMN trip_id VARCHAR(64) NULL;
ALTER TABLE bookings ADD COLUMN user_id VARCHAR(64) NULL;
ALTER TABLE bookings ADD COLUMN service_id VARCHAR(64) NULL;
ALTER TABLE bookings ADD COLUMN departure_id VARCHAR(64) NULL;
ALTER TABLE bookings ADD COLUMN vehicle_number VARCHAR(100) NULL;
ALTER TABLE bookings ADD COLUMN operator VARCHAR(100) NULL;
ALTER TABLE bookings ADD COLUMN seat_numbers VARCHAR(255) NULL;
ALTER TABLE bookings ADD COLUMN passenger_names TEXT NULL;
ALTER TABLE bookings ADD COLUMN payment_status VARCHAR(50) NULL;
ALTER TABLE bookings ADD COLUMN source VARCHAR(50) NULL DEFAULT 'INVENTORY';

ALTER TABLE bookings ADD CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_ref ON bookings (booking_reference);

-- 8. Modify audit_entries table to allow marketplace bookings without a trip
ALTER TABLE audit_entries MODIFY COLUMN trip_id VARCHAR(64) NULL;
ALTER TABLE audit_entries ADD COLUMN booking_id VARCHAR(64) NULL;
ALTER TABLE audit_entries ADD COLUMN user_id VARCHAR(64) NULL;
CREATE INDEX idx_audit_booking_id ON audit_entries (booking_id);
