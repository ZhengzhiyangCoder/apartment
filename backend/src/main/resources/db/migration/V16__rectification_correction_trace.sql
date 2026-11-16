ALTER TABLE hygiene_rectification_log DROP CHECK ck_rectification_log_action;
ALTER TABLE hygiene_rectification_log ADD CONSTRAINT ck_rectification_log_action
    CHECK (action IN ('TRIGGERED', 'FOLLOW_UP', 'VERIFIED', 'CLOSED', 'CORRECTED'));
