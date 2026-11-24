-- V13__phase12_addon_refund_deductions_sharing.sql
-- Phase 12 add-on: refund deductions, category, description, refund status, parent entry references

ALTER TABLE trip_ledger_entries ADD COLUMN category VARCHAR(50) NULL;
ALTER TABLE trip_ledger_entries ADD COLUMN description VARCHAR(255) NULL;
ALTER TABLE trip_ledger_entries ADD COLUMN parent_entry_id VARCHAR(64) NULL;
ALTER TABLE trip_ledger_entries ADD COLUMN refund_status VARCHAR(50) NULL;

CREATE INDEX idx_ledger_parent_entry_id ON trip_ledger_entries (parent_entry_id);

