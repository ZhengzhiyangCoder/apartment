-- Forward-only: retain governance fields and audit history on application rollback.
ALTER TABLE hygiene_batch
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN historical_reopened BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN suspended_from VARCHAR(16) NULL,
    ADD CONSTRAINT ck_batch_version CHECK (version >= 0),
    ADD CONSTRAINT ck_batch_suspended_from CHECK (suspended_from IS NULL OR suspended_from IN ('OPEN', 'CLOSED'));
