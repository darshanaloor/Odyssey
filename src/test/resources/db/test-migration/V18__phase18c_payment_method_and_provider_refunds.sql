-- V18__phase18c_payment_method_and_provider_refunds.sql
-- Phase 18C: Payment method on bookings and provider-issued refund ledger support

ALTER TABLE bookings ADD COLUMN payment_method VARCHAR(50) NULL DEFAULT 'CARD';
ALTER TABLE provider_schedules ALTER COLUMN days_of_week VARCHAR(100) NOT NULL;
