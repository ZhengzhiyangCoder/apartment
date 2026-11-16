CREATE TABLE hygiene_rectification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    building_id BIGINT NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6) NULL,
    closed_at TIMESTAMP(6) NULL,
    open_room_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN room_id ELSE NULL END) STORED,
    CONSTRAINT fk_rectification_room FOREIGN KEY (room_id) REFERENCES dorm_room(id),
    CONSTRAINT fk_rectification_building FOREIGN KEY (building_id) REFERENCES dorm_building(id),
    CONSTRAINT ck_rectification_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_rectification_closure CHECK ((status = 'OPEN' AND closed_at IS NULL) OR (status = 'CLOSED' AND closed_at IS NOT NULL AND verified_at IS NOT NULL)),
    UNIQUE KEY uk_rectification_open_room (open_room_id),
    KEY idx_rectification_status (status, id),
    KEY idx_rectification_building (building_id, status, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE hygiene_rectification_trigger (
    event_id BIGINT NOT NULL,
    score_id BIGINT NOT NULL,
    total_score DECIMAL(5,2) NOT NULL,
    threshold DECIMAL(5,2) NOT NULL,
    triggered_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id, score_id),
    CONSTRAINT fk_rectification_trigger_event FOREIGN KEY (event_id) REFERENCES hygiene_rectification(id),
    CONSTRAINT fk_rectification_trigger_score FOREIGN KEY (score_id) REFERENCES hygiene_score(id),
    CONSTRAINT ck_rectification_trigger_low CHECK (total_score >= 0 AND total_score < threshold AND threshold <= 100)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE hygiene_rectification_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(64) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_rectification_log_event FOREIGN KEY (event_id) REFERENCES hygiene_rectification(id),
    CONSTRAINT fk_rectification_log_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    CONSTRAINT ck_rectification_log_action CHECK (action IN ('TRIGGERED', 'FOLLOW_UP', 'VERIFIED', 'CLOSED')),
    KEY idx_rectification_log_event (event_id, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
