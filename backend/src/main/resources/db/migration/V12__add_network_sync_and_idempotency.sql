-- Migration V12: Add idempotency client identifiers for photos and additional work
ALTER TABLE work_photos ADD COLUMN client_photo_id VARCHAR(64);
CREATE INDEX idx_work_photos_client_photo ON work_photos(work_id, client_photo_id);

ALTER TABLE additional_works ADD COLUMN client_item_id VARCHAR(64);
CREATE INDEX idx_additional_works_client_item ON additional_works(work_id, client_item_id);
