-- V19__provider_booking_confirmation.sql
-- Support for PENDING_PROVIDER_CONFIRMATION and auto-healing active provider services

ALTER TABLE bookings ADD COLUMN confirmation_expires_at TIMESTAMP NULL;
ALTER TABLE bookings ADD COLUMN provider_confirmation_status VARCHAR(50) NULL;

-- Auto-heal any legacy provider services where the schedule is active
UPDATE services SET active = true WHERE id IN (SELECT service_id FROM provider_schedules WHERE active = true);
