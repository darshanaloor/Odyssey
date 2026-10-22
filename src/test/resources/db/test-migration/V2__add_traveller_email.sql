-- V2__add_traveller_email.sql
-- Add traveller_email column to trips table
ALTER TABLE trips ADD COLUMN traveller_email VARCHAR(100) NULL;
