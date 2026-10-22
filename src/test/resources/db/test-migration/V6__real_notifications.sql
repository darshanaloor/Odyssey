-- V6__real_notifications.sql
-- Add notification preferences to trips
ALTER TABLE trips ADD COLUMN notify_email BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE trips ADD COLUMN notify_sms BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE trips ADD COLUMN notify_whatsapp BOOLEAN NOT NULL DEFAULT FALSE;

-- Add hotel contact email to bookings
ALTER TABLE bookings ADD COLUMN hotel_contact_email VARCHAR(100) NULL;

-- Create notifications table
CREATE TABLE notifications (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    disruption_id VARCHAR(64) NULL,
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient_masked VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error TEXT NULL,
    provider_message_id VARCHAR(100) NULL,
    dedup_key VARCHAR(150) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_notifications_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_notifications_trip_id (trip_id),
    INDEX idx_notifications_created_at (created_at),
    INDEX idx_notifications_dedup_key (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
