-- V5: Add source and actor columns to disruptions table
ALTER TABLE disruptions ADD COLUMN source VARCHAR(50) NULL DEFAULT 'MANUAL';
ALTER TABLE disruptions ADD COLUMN actor VARCHAR(50) NULL DEFAULT 'USER';
