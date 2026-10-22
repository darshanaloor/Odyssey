-- V1__init.sql
-- Odyssey Database Schema Initialization

CREATE TABLE trips (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    traveller_name VARCHAR(255) NOT NULL,
    traveller_phone VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_trips_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_constraints (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL UNIQUE,
    max_budget DECIMAL(10,2) NOT NULL,
    must_arrive_by DATETIME,
    avoid_overnight_travel BOOLEAN DEFAULT FALSE,
    preferred_modes VARCHAR(255),
    CONSTRAINT fk_constraints_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_constraints_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hard_commitments (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    commitment_time DATETIME NOT NULL,
    location VARCHAR(255),
    CONSTRAINT fk_commitments_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_commitments_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bookings (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    from_location VARCHAR(255) NOT NULL,
    to_location VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    cost DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_bookings_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_bookings_trip_id (trip_id),
    INDEX idx_bookings_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking_dependencies (
    booking_id VARCHAR(64) NOT NULL,
    depends_on_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (booking_id, depends_on_id),
    CONSTRAINT fk_dep_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_depends FOREIGN KEY (depends_on_id) REFERENCES bookings(id) ON DELETE CASCADE,
    INDEX idx_dep_booking (booking_id),
    INDEX idx_dep_depends (depends_on_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE disruptions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    booking_id VARCHAR(64),
    type VARCHAR(50) NOT NULL,
    delay_minutes INT DEFAULT 0,
    reason TEXT,
    disruption_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_disruptions_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_disruptions_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recovery_options (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    label VARCHAR(50) NOT NULL,
    score DOUBLE NOT NULL,
    total_cost DECIMAL(10,2) NOT NULL,
    arrival_time DATETIME NOT NULL,
    explanation TEXT,
    source VARCHAR(50) NOT NULL DEFAULT 'INVENTORY',
    constraints_met TEXT,
    constraints_violated TEXT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_recovery_options_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_recovery_options_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE option_legs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    option_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    provider VARCHAR(255) NOT NULL,
    from_location VARCHAR(255) NOT NULL,
    to_location VARCHAR(255) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    cost DECIMAL(10,2) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'INVENTORY',
    leg_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_legs_option FOREIGN KEY (option_id) REFERENCES recovery_options(id) ON DELETE CASCADE,
    INDEX idx_option_legs_option_id (option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recovery_plans (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    option_id VARCHAR(64),
    new_leg_id VARCHAR(64),
    replacement_cab_id VARCHAR(64),
    trade_off_explanation TEXT,
    hotel_message TEXT,
    net_trip_cost DECIMAL(10,2) NOT NULL,
    max_budget DECIMAL(10,2) NOT NULL,
    remaining_budget DECIMAL(10,2) NOT NULL,
    cost_breakdown_json TEXT,
    itinerary_diff_json TEXT,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_recovery_plans_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_recovery_plans_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plan_checklist_items (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    text VARCHAR(500) NOT NULL,
    done BOOLEAN NOT NULL DEFAULT FALSE,
    item_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_checklist_plan FOREIGN KEY (plan_id) REFERENCES recovery_plans(id) ON DELETE CASCADE,
    INDEX idx_checklist_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_entries (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id VARCHAR(64) NOT NULL,
    timestamp DATETIME NOT NULL,
    actor VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    CONSTRAINT fk_audit_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    INDEX idx_audit_trip_id (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
