-- V12__phase12_cost_and_refund_ledger.sql
-- Cost and refund ledger tracking for disrupted and recovered trips

CREATE TABLE trip_ledger_entries (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    booking_id VARCHAR(64) NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    basis VARCHAR(50) NOT NULL,
    note TEXT NULL,
    reference VARCHAR(100) NULL,
    received_on DATE NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_ledger_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_ledger_trip_id (trip_id),
    INDEX idx_ledger_booking_id (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

