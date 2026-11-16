CREATE TABLE hygiene_score (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    total_score DECIMAL(5,2) NOT NULL,
    threshold DECIMAL(5,2) NOT NULL,
    submitted_by BIGINT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_score_expected FOREIGN KEY (batch_id, room_id) REFERENCES hygiene_batch_room(batch_id, room_id),
    CONSTRAINT fk_score_actor FOREIGN KEY (submitted_by) REFERENCES sys_user(id),
    CONSTRAINT ck_score_total CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT ck_score_threshold CHECK (threshold BETWEEN 0 AND 100),
    UNIQUE KEY uk_score_batch_room (batch_id, room_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE hygiene_score_dimension (
    score_id BIGINT NOT NULL,
    dimension_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    weight DECIMAL(5,2) NOT NULL,
    score INT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (score_id, dimension_id),
    CONSTRAINT fk_score_dimension_score FOREIGN KEY (score_id) REFERENCES hygiene_score(id),
    CONSTRAINT fk_score_dimension_definition FOREIGN KEY (dimension_id) REFERENCES hygiene_scheme_dimension(id),
    CONSTRAINT ck_score_dimension_value CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_score_dimension_weight CHECK (weight BETWEEN 0 AND 100),
    CONSTRAINT ck_score_dimension_order CHECK (sort_order BETWEEN 1 AND 20),
    UNIQUE KEY uk_score_dimension_order (score_id, sort_order)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
