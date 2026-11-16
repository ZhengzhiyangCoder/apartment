ALTER TABLE hygiene_score ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_score_version CHECK (version >= 0);

CREATE TABLE hygiene_score_correction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    score_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    before_data JSON NOT NULL,
    after_data JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_correction_score FOREIGN KEY (score_id) REFERENCES hygiene_score(id),
    CONSTRAINT fk_correction_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    CONSTRAINT ck_correction_version CHECK (version > 0),
    UNIQUE KEY uk_correction_version (score_id, version)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
