CREATE TABLE repair_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    submitted_by BIGINT NOT NULL,
    residency_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    student_name VARCHAR(64) NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    bed_no VARCHAR(32) NOT NULL,
    category VARCHAR(24) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
    request_id CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_repair_submitter FOREIGN KEY (submitted_by) REFERENCES sys_user(id),
    CONSTRAINT fk_repair_residency FOREIGN KEY (residency_id) REFERENCES housing_residency(id),
    CONSTRAINT fk_repair_student FOREIGN KEY (student_id) REFERENCES student_profile(id),
    CONSTRAINT fk_repair_building FOREIGN KEY (building_id) REFERENCES dorm_building(id),
    CONSTRAINT fk_repair_room FOREIGN KEY (room_id) REFERENCES dorm_room(id),
    CONSTRAINT fk_repair_bed FOREIGN KEY (bed_id) REFERENCES dorm_bed(id),
    CONSTRAINT ck_repair_category CHECK (category IN ('PLUMBING', 'ELECTRICAL', 'DOORS', 'FURNITURE', 'OTHER')),
    CONSTRAINT ck_repair_status CHECK (status IN ('PENDING_ASSIGNMENT', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    UNIQUE KEY uk_repair_request (submitted_by, request_id),
    KEY idx_repair_owner (submitted_by, id),
    KEY idx_repair_status (status, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE repair_image (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    storage_key CHAR(40) NOT NULL,
    byte_size BIGINT NOT NULL,
    CONSTRAINT fk_repair_image_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    UNIQUE KEY uk_repair_image_key (storage_key)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE repair_status_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    note VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_repair_log_order FOREIGN KEY (order_id) REFERENCES repair_order(id),
    CONSTRAINT fk_repair_log_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    KEY idx_repair_log_order (order_id, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
