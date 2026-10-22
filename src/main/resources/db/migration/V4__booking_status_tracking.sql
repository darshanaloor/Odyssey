-- V4: Add booking status tracking columns for automatic disruption detection
ALTER TABLE bookings ADD COLUMN last_status_state VARCHAR(20) NULL;
ALTER TABLE bookings ADD COLUMN last_delay_minutes INT NULL;
ALTER TABLE bookings ADD COLUMN last_checked_at DATETIME NULL;
ALTER TABLE bookings ADD COLUMN last_status_source VARCHAR(20) NULL;
ALTER TABLE bookings ADD COLUMN status_check_error VARCHAR(255) NULL;
ALTER TABLE bookings ADD COLUMN last_dedup_key VARCHAR(100) NULL;
