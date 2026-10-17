-- Repair media URLs written before UserFacingTextSanitizer learned to
-- distinguish machine fields from learner-facing copy.
-- The replacements are intentionally narrow and idempotent.

START TRANSACTION;

-- Use UTF-8 hex literals so the migration is safe when invoked from a
-- Windows shell whose code page is not UTF-8.
SET @bad_domain = CONVERT(
    0x706C6174666F726D2D6F7574707574732E414920E58AA9E6898B2D61692E7370616365
    USING utf8mb4);
SET @good_domain = CONVERT(
    0x706C6174666F726D2D6F7574707574732E61676E65732D61692E7370616365
    USING utf8mb4);
SET @bad_video_path = CONVERT(
    0x2F766964656F732FE5AA92E4BD93E69C8DE58AA12D76322E302F
    USING utf8mb4);
SET @good_video_path = CONVERT(
    0x2F766964656F732F61676E65732D766964656F2D76322E302F
    USING utf8mb4);

UPDATE edu_resource
SET source_url = REPLACE(source_url, @bad_domain, @good_domain),
    cover_url = REPLACE(cover_url, @bad_domain, @good_domain),
    content = REPLACE(
        REPLACE(content, @bad_domain, @good_domain),
        @bad_video_path,
        @good_video_path)
WHERE INSTR(source_url, @bad_domain) > 0
   OR INSTR(cover_url, @bad_domain) > 0
   OR INSTR(content, @bad_domain) > 0
   OR INSTR(content, @bad_video_path) > 0;

UPDATE conversation_history
SET metadata_json = REPLACE(
        REPLACE(metadata_json, @bad_domain, @good_domain),
        @bad_video_path,
        @good_video_path),
    content = REPLACE(
        REPLACE(content, @bad_domain, @good_domain),
        @bad_video_path,
        @good_video_path)
WHERE INSTR(metadata_json, @bad_domain) > 0
   OR INSTR(metadata_json, @bad_video_path) > 0
   OR INSTR(content, @bad_domain) > 0
   OR INSTR(content, @bad_video_path) > 0;

COMMIT;
