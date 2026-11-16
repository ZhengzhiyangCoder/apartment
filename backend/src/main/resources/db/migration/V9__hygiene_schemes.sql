CREATE TABLE hygiene_scheme (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    threshold DECIMAL(5,2) NOT NULL DEFAULT 60.00,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    source_id BIGINT NULL,
    active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    activated_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_scheme_source FOREIGN KEY (source_id) REFERENCES hygiene_scheme(id),
    CONSTRAINT ck_scheme_threshold CHECK (threshold >= 0 AND threshold <= 100),
    CONSTRAINT ck_scheme_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    UNIQUE KEY uk_scheme_active (active_slot),
    KEY idx_scheme_status (status, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;

CREATE TABLE hygiene_scheme_dimension (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scheme_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    weight DECIMAL(5,2) NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_dimension_scheme FOREIGN KEY (scheme_id) REFERENCES hygiene_scheme(id),
    CONSTRAINT ck_dimension_weight CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT ck_dimension_order CHECK (sort_order BETWEEN 1 AND 20),
    UNIQUE KEY uk_dimension_name (scheme_id, name),
    UNIQUE KEY uk_dimension_order (scheme_id, sort_order)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
