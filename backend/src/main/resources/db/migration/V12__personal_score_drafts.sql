-- Forward-only. Rollback the application first; retain drafts until explicitly exported/retired.
CREATE TABLE hygiene_score_draft (
    actor_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    building_name VARCHAR(64) NOT NULL,
    room_no VARCHAR(32) NOT NULL,
    dimensions JSON NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (actor_id, batch_id, room_id),
    CONSTRAINT fk_draft_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
    CONSTRAINT fk_draft_batch FOREIGN KEY (batch_id) REFERENCES hygiene_batch(id),
    CONSTRAINT fk_draft_room FOREIGN KEY (room_id) REFERENCES dorm_room(id),
    CONSTRAINT ck_draft_version CHECK (version > 0),
    KEY ix_draft_owner_updated (actor_id, updated_at, batch_id, room_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
