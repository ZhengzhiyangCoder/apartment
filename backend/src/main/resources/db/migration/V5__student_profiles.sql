CREATE TABLE student_profile (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    student_no VARCHAR(32) NOT NULL,
    full_name VARCHAR(64) NOT NULL,
    college VARCHAR(64) NOT NULL,
    major VARCHAR(64) NOT NULL,
    class_name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    UNIQUE KEY uk_student_user (user_id),
    UNIQUE KEY uk_student_number (student_no),
    KEY idx_student_enabled (enabled, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
