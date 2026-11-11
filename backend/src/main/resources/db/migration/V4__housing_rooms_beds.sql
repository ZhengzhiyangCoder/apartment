CREATE TABLE dorm_room (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    building_id BIGINT NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    floor INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_room_building FOREIGN KEY (building_id) REFERENCES dorm_building(id),
    CONSTRAINT ck_room_floor CHECK (floor BETWEEN 1 AND 100),
    UNIQUE KEY uk_room_building_number (building_id, room_no),
    KEY idx_room_enabled (enabled, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE dorm_bed (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    bed_no VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_bed_room FOREIGN KEY (room_id) REFERENCES dorm_room(id),
    UNIQUE KEY uk_bed_room_number (room_id, bed_no),
    KEY idx_bed_enabled (enabled, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
