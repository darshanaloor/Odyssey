-- V7__phase9_user_accounts_and_handoff.sql
-- User accounts, trip ownership, and booking hand-off

CREATE TABLE users (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE trips ADD COLUMN user_id VARCHAR(64) NULL;
ALTER TABLE trips ADD CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_trips_user_id ON trips (user_id);

ALTER TABLE bookings ADD COLUMN booking_reference VARCHAR(100) NULL;
