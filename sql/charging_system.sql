CREATE DATABASE IF NOT EXISTS charging_system
DEFAULT CHARACTER SET utf8mb4;

USE charging_system;

CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  phone VARCHAR(20),
  plate_no VARCHAR(20),
  battery_capacity DOUBLE NOT NULL,
  role VARCHAR(20) DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS charging_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  mode VARCHAR(10) NOT NULL,
  requested_kwh DOUBLE NOT NULL,
  queue_number VARCHAR(10),
  queue_type VARCHAR(10),
  status VARCHAR(20) DEFAULT 'WAITING',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL,
  pile_id BIGINT,
  actual_kwh DOUBLE NOT NULL,
  duration_hours DOUBLE,
  electricity_fee DECIMAL(10,2),
  service_fee DECIMAL(10,2),
  total_fee DECIMAL(10,2),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS charging_pile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pile_code VARCHAR(20),
  type VARCHAR(10),
  power DOUBLE,
  status VARCHAR(20),
  total_charge_count INT DEFAULT 0,
  total_charge_time DOUBLE DEFAULT 0,
  total_charge_kwh DOUBLE DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pile_queue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pile_id BIGINT,
  request_id BIGINT,
  position_no INT,
  status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS waiting_queue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT,
  queue_number VARCHAR(10),
  mode VARCHAR(10),
  position_no INT
);

CREATE TABLE IF NOT EXISTS fault_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pile_id BIGINT,
  fault_time DATETIME,
  recover_time DATETIME,
  schedule_policy VARCHAR(20),
  remark VARCHAR(255)
);

INSERT INTO charging_pile
(pile_code, type, power, status)
SELECT 'F1', 'FAST', 30, 'IDLE'
WHERE NOT EXISTS (SELECT 1 FROM charging_pile WHERE pile_code = 'F1');

INSERT INTO charging_pile
(pile_code, type, power, status)
SELECT 'F2', 'FAST', 30, 'IDLE'
WHERE NOT EXISTS (SELECT 1 FROM charging_pile WHERE pile_code = 'F2');

INSERT INTO charging_pile
(pile_code, type, power, status)
SELECT 'F3', 'FAST', 30, 'IDLE'
WHERE NOT EXISTS (SELECT 1 FROM charging_pile WHERE pile_code = 'F3');

INSERT INTO charging_pile
(pile_code, type, power, status)
SELECT 'T1', 'SLOW', 10, 'IDLE'
WHERE NOT EXISTS (SELECT 1 FROM charging_pile WHERE pile_code = 'T1');

INSERT INTO charging_pile
(pile_code, type, power, status)
SELECT 'T2', 'SLOW', 10, 'IDLE'
WHERE NOT EXISTS (SELECT 1 FROM charging_pile WHERE pile_code = 'T2');
