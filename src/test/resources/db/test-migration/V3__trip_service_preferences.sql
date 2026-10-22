-- V3__trip_service_preferences.sql
-- Add service preferences (destination cab and hotel requirements) to trips table
ALTER TABLE trips ADD COLUMN needs_destination_cab BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN destination_cab_dropoff VARCHAR(255) NULL;
ALTER TABLE trips ADD COLUMN needs_hotel BOOLEAN NOT NULL DEFAULT FALSE;
