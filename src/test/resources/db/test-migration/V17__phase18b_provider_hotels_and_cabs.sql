-- Phase 18B: Provider Portal Part 2
-- Hotels, Room Types, Date-based Availability, and Cab Services

-- 1. Modify hotels table for provider listings
ALTER TABLE hotels ADD COLUMN provider_id VARCHAR(64) NULL;
ALTER TABLE hotels ADD COLUMN description VARCHAR(500) NULL;
ALTER TABLE hotels ADD COLUMN check_in_time VARCHAR(10) NULL DEFAULT '14:00';
ALTER TABLE hotels ADD COLUMN check_out_time VARCHAR(10) NULL DEFAULT '11:00';
ALTER TABLE hotels ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE hotels ADD COLUMN created_at DATETIME NULL;
ALTER TABLE hotels ADD COLUMN updated_at DATETIME NULL;
ALTER TABLE hotels MODIFY COLUMN price_per_night DECIMAL(10,2) NULL;
ALTER TABLE hotels MODIFY COLUMN rating DECIMAL(3,1) NULL;
ALTER TABLE hotels MODIFY COLUMN rooms_total INT NULL;
CREATE INDEX idx_hotels_provider ON hotels(provider_id);

-- 2. Hotel Room Types table
CREATE TABLE hotel_room_types (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    hotel_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    max_guests INT NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL,
    rooms_total INT NOT NULL,
    amenities VARCHAR(200) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_room_types_hotel FOREIGN KEY (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_room_types_hotel ON hotel_room_types(hotel_id);

-- 3. Hotel Room Date-based Availability table
CREATE TABLE hotel_room_availabilities (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    room_type_id VARCHAR(64) NOT NULL,
    hotel_id VARCHAR(64) NOT NULL,
    stay_date DATE NOT NULL,
    rooms_total INT NOT NULL,
    rooms_booked INT NOT NULL DEFAULT 0,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_avail_room_type FOREIGN KEY (room_type_id) REFERENCES hotel_room_types(id) ON DELETE CASCADE,
    CONSTRAINT fk_avail_hotel FOREIGN KEY (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE,
    CONSTRAINT uq_room_stay_date UNIQUE (room_type_id, stay_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_avail_hotel_date ON hotel_room_availabilities(hotel_id, stay_date);

-- 4. Seed default room types for existing seeded/generated hotels
INSERT INTO hotel_room_types (id, hotel_id, name, max_guests, price_per_night, rooms_total, amenities, active, created_at, updated_at)
SELECT CONCAT('RT-DEF-', id), id, 'Standard Room', 2, COALESCE(price_per_night, 1500.00), COALESCE(rooms_total, 10), 'Standard Amenities', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM hotels
WHERE id NOT IN (SELECT hotel_id FROM hotel_room_types);

-- 5. Modify cab_services table for provider listings
ALTER TABLE cab_services ADD COLUMN provider_id VARCHAR(64) NULL;
ALTER TABLE cab_services ADD COLUMN vehicle_category VARCHAR(30) NULL DEFAULT 'SEDAN';
ALTER TABLE cab_services ADD COLUMN seats INT NULL DEFAULT 4;
ALTER TABLE cab_services ADD COLUMN operating_hours VARCHAR(100) NULL DEFAULT '06:00 - 23:00';
ALTER TABLE cab_services ADD COLUMN vehicle_number VARCHAR(30) NULL;
ALTER TABLE cab_services ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE cab_services ADD COLUMN created_at DATETIME NULL;
ALTER TABLE cab_services ADD COLUMN updated_at DATETIME NULL;
CREATE INDEX idx_cabs_provider ON cab_services(provider_id);

-- 6. Modify bookings table for hotel room type and cannot honour tracking
ALTER TABLE bookings ADD COLUMN hotel_room_type_id VARCHAR(64) NULL;
ALTER TABLE bookings ADD COLUMN cannot_honour BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN cannot_honour_reason VARCHAR(255) NULL;
ALTER TABLE bookings ADD COLUMN cannot_honour_at DATETIME NULL;
