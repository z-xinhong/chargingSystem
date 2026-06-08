USE charging_system;

ALTER TABLE user
ADD COLUMN plate_no VARCHAR(20) AFTER phone;
