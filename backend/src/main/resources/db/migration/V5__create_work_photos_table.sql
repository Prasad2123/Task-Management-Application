-- V5: Create work_photos table
CREATE TABLE work_photos (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES works(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL'
        CHECK (category IN ('SITE_INSPECTION','TREATMENT_APPLICATION','EQUIPMENT_CHECK',
                            'SAFETY_PPE','ADDITIONAL_WORK','GENERAL')),
    caption TEXT,
    uploaded_by_id BIGINT NOT NULL REFERENCES users(id),
    storage_reference TEXT,
    upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (upload_status IN ('PENDING','UPLOADING','UPLOADED','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_photos_work ON work_photos(work_id);
