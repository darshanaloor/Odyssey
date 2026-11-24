-- Phase 19: Provider daily operations dashboard, alternative-vehicle rerouting, privacy-adjusted cab contacts, and tiered refunds

ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMP NULL;
ALTER TABLE bookings ADD COLUMN cancelled_by VARCHAR(100) NULL;
ALTER TABLE bookings ADD COLUMN assigned_driver_name VARCHAR(100) NULL;
ALTER TABLE bookings ADD COLUMN alternative_departure_id VARCHAR(64) NULL;

ALTER TABLE service_departures ADD COLUMN alternative_departure_id VARCHAR(64) NULL;

ALTER TABLE recovery_options ADD COLUMN best_available_note VARCHAR(255) NULL;
