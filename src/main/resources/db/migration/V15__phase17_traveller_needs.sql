-- V15__phase17_traveller_needs.sql
-- Add traveller needs and sensitive emergency contact columns to trips table
ALTER TABLE trips ADD COLUMN has_mobility_assistance BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN has_elderly_traveller BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN has_travelling_with_children BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN has_medical_needs BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trips ADD COLUMN special_needs_note VARCHAR(300) NULL;
ALTER TABLE trips ADD COLUMN emergency_contact_name VARCHAR(100) NULL;
ALTER TABLE trips ADD COLUMN emergency_contact_phone VARCHAR(20) NULL;
