CREATE TABLE hygiene_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    iso_year INT NOT NULL,
    iso_week INT NOT NULL,
    week_start DATE NOT NULL,
    scheme_id BIGINT NOT NULL,
    scheme_name VARCHAR(64) NOT NULL,
    threshold DECIMAL(5,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_batch_scheme FOREIGN KEY (scheme_id) REFERENCES hygiene_scheme(id),
    CONSTRAINT fk_batch_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT ck_batch_week CHECK (iso_week BETWEEN 1 AND 53),
    CONSTRAINT ck_batch_status CHECK (status IN ('OPEN', 'CLOSED', 'SUSPENDED')),
    CONSTRAINT ck_batch_threshold CHECK (threshold BETWEEN 0 AND 100),
    UNIQUE KEY uk_batch_week (iso_year, iso_week),
    UNIQUE KEY uk_batch_start (week_start)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE hygiene_batch_room (
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    PRIMARY KEY (batch_id, room_id),
    CONSTRAINT fk_expected_batch FOREIGN KEY (batch_id) REFERENCES hygiene_batch(id),
    CONSTRAINT fk_expected_room FOREIGN KEY (room_id) REFERENCES dorm_room(id),
    CONSTRAINT fk_expected_building FOREIGN KEY (building_id) REFERENCES dorm_building(id),
    KEY idx_expected_location (batch_id, building_id, room_no, room_id),
    KEY idx_expected_building (building_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
