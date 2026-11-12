-- Forward-only history: rollback requires a compensating migration, never deleting occupied/history rows.
CREATE TABLE housing_residency (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    bed_no VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_by BIGINT NOT NULL,
    ended_at TIMESTAMP(6) NULL,
    ended_by BIGINT NULL,
    end_reason VARCHAR(16) NULL,
    end_note VARCHAR(200) NULL,
    active_student_id BIGINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN student_id ELSE NULL END) VIRTUAL,
    active_bed_id BIGINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN bed_id ELSE NULL END) VIRTUAL,
    CONSTRAINT fk_residency_student FOREIGN KEY (student_id) REFERENCES student_profile(id),
    CONSTRAINT fk_residency_bed FOREIGN KEY (bed_id) REFERENCES dorm_bed(id),
    CONSTRAINT fk_residency_started_by FOREIGN KEY (started_by) REFERENCES sys_user(id),
    CONSTRAINT fk_residency_ended_by FOREIGN KEY (ended_by) REFERENCES sys_user(id),
    CONSTRAINT ck_residency_end CHECK (
        (ended_at IS NULL AND ended_by IS NULL AND end_reason IS NULL AND end_note IS NULL)
        OR (ended_at IS NOT NULL AND ended_at >= started_at AND ended_by IS NOT NULL
            AND end_reason IS NOT NULL AND end_reason IN ('TRANSFER', 'CHECKOUT') AND end_note IS NOT NULL)
    ),
    UNIQUE KEY uk_residency_current_student (active_student_id),
    UNIQUE KEY uk_residency_current_bed (active_bed_id),
    KEY idx_residency_student_history (student_id, id),
    KEY idx_residency_current (ended_at, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
