-- Migration V10: Phase 10C Marketplace recovery and status fields on option legs
ALTER TABLE option_legs ADD COLUMN service_departure_id VARCHAR(64) NULL;
ALTER TABLE option_legs ADD COLUMN operator VARCHAR(100) NULL;
ALTER TABLE option_legs ADD COLUMN vehicle_number VARCHAR(100) NULL;
ALTER TABLE option_legs ADD COLUMN seats_available INT NULL;
ALTER TABLE option_legs ADD COLUMN schedule_label VARCHAR(100) NULL;
