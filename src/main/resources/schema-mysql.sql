CREATE TABLE IF NOT EXISTS users (
 user_id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 email VARCHAR(255) NOT NULL, password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL,
 CONSTRAINT uq_users_email UNIQUE(email), CONSTRAINT ck_users_role CHECK(role IN ('CUSTOMER','PROVIDER'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS providers (
 provider_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, bio VARCHAR(1000),
 CONSTRAINT fk_providers_user FOREIGN KEY(user_id) REFERENCES users(user_id), CONSTRAINT uq_providers_user UNIQUE(user_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS services (
 service_id BIGINT AUTO_INCREMENT PRIMARY KEY, provider_id BIGINT NOT NULL, name VARCHAR(150) NOT NULL,
 description VARCHAR(1000), duration_minutes INT NOT NULL,
 CONSTRAINT fk_services_provider FOREIGN KEY(provider_id) REFERENCES providers(provider_id),
 CONSTRAINT ck_services_duration CHECK(duration_minutes > 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS availability_slots (
 slot_id BIGINT AUTO_INCREMENT PRIMARY KEY, service_id BIGINT NOT NULL,
 start_time TIMESTAMP NOT NULL, end_time TIMESTAMP NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT fk_slots_service FOREIGN KEY(service_id) REFERENCES services(service_id),
 CONSTRAINT ck_slots_time CHECK(end_time > start_time), CONSTRAINT uq_slots_service_window UNIQUE(service_id,start_time,end_time)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS appointments (
 appointment_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, slot_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 active_slot_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('BOOKED','COMPLETED') THEN slot_id ELSE NULL END) STORED,
 CONSTRAINT fk_appointments_user FOREIGN KEY(user_id) REFERENCES users(user_id),
 CONSTRAINT fk_appointments_slot FOREIGN KEY(slot_id) REFERENCES availability_slots(slot_id),
 CONSTRAINT ck_appointment_status CHECK(status IN ('BOOKED','CANCELLED','COMPLETED')),
 CONSTRAINT uq_appointment_active_slot UNIQUE(active_slot_id)
) ENGINE=InnoDB;
