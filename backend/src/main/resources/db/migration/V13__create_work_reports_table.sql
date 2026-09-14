CREATE TABLE work_reports (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES works(id) ON DELETE CASCADE,
    report_number VARCHAR(64) NOT NULL UNIQUE,
    storage_reference VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'application/pdf',
    file_size BIGINT NOT NULL DEFAULT 0,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    version INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_work_reports_work_id ON work_reports(work_id);
