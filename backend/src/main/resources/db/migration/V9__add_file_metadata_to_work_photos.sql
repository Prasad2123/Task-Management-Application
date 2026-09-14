-- V9: Add file metadata and photo_url to work_photos table
ALTER TABLE work_photos
    ADD COLUMN file_name VARCHAR(255),
    ADD COLUMN content_type VARCHAR(100),
    ADD COLUMN file_size BIGINT,
    ADD COLUMN photo_url TEXT;
