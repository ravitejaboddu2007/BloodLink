

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    role VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    age INT,
    blood_group VARCHAR(10),
    city VARCHAR(100),
    state VARCHAR(100),
    address VARCHAR(255),
    registration_number VARCHAR(100),
    operating_hours VARCHAR(100),
    lat DOUBLE,
    lng DOUBLE,
    location_updated_at DATETIME,
    available BOOLEAN,
    last_donation VARCHAR(30),
    report_data LONGTEXT,
    report_name VARCHAR(255),
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Inventory Table
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blood_bank_id VARCHAR(64) NOT NULL UNIQUE,
    stock_a_pos INT NOT NULL DEFAULT 0,
    stock_a_neg INT NOT NULL DEFAULT 0,
    stock_b_pos INT NOT NULL DEFAULT 0,
    stock_b_neg INT NOT NULL DEFAULT 0,
    stock_o_pos INT NOT NULL DEFAULT 0,
    stock_o_neg INT NOT NULL DEFAULT 0,
    stock_ab_pos INT NOT NULL DEFAULT 0,
    stock_ab_neg INT NOT NULL DEFAULT 0,
    last_updated_json VARCHAR(1000),
    CONSTRAINT fk_inventory_user FOREIGN KEY (blood_bank_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Blood Requests Table
CREATE TABLE IF NOT EXISTS blood_requests (
    id VARCHAR(64) PRIMARY KEY,
    hospital_id VARCHAR(64) NOT NULL,
    patient_name VARCHAR(150) NOT NULL,
    blood_group VARCHAR(10) NOT NULL,
    units INT NOT NULL,
    secured_units INT DEFAULT 0,
    urgency VARCHAR(20) NOT NULL,
    contact VARCHAR(50) NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    radius INT NOT NULL,
    request_type VARCHAR(20) NOT NULL DEFAULT 'EMERGENCY',
    reason VARCHAR(250),
    operation_time DATETIME,
    accumulation_deadline DATETIME,
    donor_alerts_sent BOOLEAN DEFAULT FALSE,
    remaining_units_at_donor_alert INT,
    created_at DATETIME NOT NULL,
    fulfilled_at DATETIME,
    CONSTRAINT fk_requests_hospital FOREIGN KEY (hospital_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Request Blood Banks Junction Table
CREATE TABLE IF NOT EXISTS request_blood_banks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    blood_bank_id VARCHAR(64) NOT NULL,
    distance DOUBLE,
    available_units_at_match INT,
    status VARCHAR(30),
    response_status VARCHAR(30),
    units_secured INT DEFAULT 0,
    responded_at DATETIME,
    unavailable BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_req_banks_req FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_req_banks_bank FOREIGN KEY (blood_bank_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Request Donors Junction Table
CREATE TABLE IF NOT EXISTS request_donors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    donor_id VARCHAR(64) NOT NULL,
    alert_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    responded_at DATETIME,
    confirmed_at DATETIME,
    CONSTRAINT fk_req_donors_req FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_req_donors_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Donation History Table
CREATE TABLE IF NOT EXISTS donation_history (
    id VARCHAR(64) PRIMARY KEY,
    donor_id VARCHAR(64) NOT NULL,
    date VARCHAR(30) NOT NULL,
    location VARCHAR(200) NOT NULL,
    units INT NOT NULL,
    notes VARCHAR(500),
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_history_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Reviews Table
CREATE TABLE IF NOT EXISTS reviews (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    rating INT NOT NULL,
    text VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
