-- Compatibility patch only. Execute this file once ONLY when an older version
-- of 13_quiz_practice_session.sql has already created quiz_practice_session
-- without current_position. New environments must run the current 13 file and
-- must NOT run this patch, because 13 already creates the column.
ALTER TABLE quiz_practice_session
    ADD COLUMN current_position int NOT NULL DEFAULT 1 AFTER correct_count;
