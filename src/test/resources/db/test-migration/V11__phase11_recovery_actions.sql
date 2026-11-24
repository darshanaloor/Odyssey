ALTER TABLE plan_checklist_items ADD COLUMN trip_id VARCHAR(64) NULL;
ALTER TABLE plan_checklist_items ADD COLUMN type VARCHAR(50) NULL;
ALTER TABLE plan_checklist_items ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE plan_checklist_items ADD COLUMN note TEXT NULL;
ALTER TABLE plan_checklist_items ADD COLUMN completed_at DATETIME NULL;
ALTER TABLE plan_checklist_items ADD COLUMN related_booking_id VARCHAR(64) NULL;
ALTER TABLE plan_checklist_items ADD COLUMN booking_reference VARCHAR(100) NULL;
ALTER TABLE plan_checklist_items ADD COLUMN contact_info VARCHAR(255) NULL;

CREATE INDEX idx_checklist_trip_id ON plan_checklist_items (trip_id);

