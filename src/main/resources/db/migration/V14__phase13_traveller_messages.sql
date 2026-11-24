-- V14__phase13_traveller_messages.sql
-- Traveller Communication Assistant: message history, drafts, channels and verification

CREATE TABLE traveller_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    draft_kind VARCHAR(50) NOT NULL,
    booking_id VARCHAR(64) NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    channel VARCHAR(20) NULL,
    status VARCHAR(30) NOT NULL,
    generated_by VARCHAR(20) NOT NULL,
    recipient_masked VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    CONSTRAINT fk_messages_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_msg_trip_created (trip_id, created_at),
    INDEX idx_msg_booking_id (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

