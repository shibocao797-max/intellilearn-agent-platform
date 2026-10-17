-- Preserve the question shown at practice-session creation time.
-- Run once on existing databases; fresh databases should include this column before creating sessions.
ALTER TABLE quiz_practice_item
    ADD COLUMN question_snapshot_json MEDIUMTEXT DEFAULT NULL AFTER question_id;
