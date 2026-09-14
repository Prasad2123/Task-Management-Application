-- V2: Create works table
CREATE TABLE works (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    work_type VARCHAR(100),
    description TEXT,
    notes VARCHAR(100),
    scheduled_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED'
        CHECK (status IN ('ASSIGNED','IN_PROGRESS','SUBMITTED_FOR_REVIEW',
                          'POC_APPROVED','SUPERVISOR_APPROVED','REJECTED','COMPLETED')),
    service_boy_id BIGINT NOT NULL REFERENCES users(id),
    poc_id BIGINT NOT NULL REFERENCES users(id),
    supervisor_id BIGINT NOT NULL REFERENCES users(id),
    company_name VARCHAR(200),
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    start_time TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_works_service_boy ON works(service_boy_id);
CREATE INDEX idx_works_poc ON works(poc_id);
CREATE INDEX idx_works_supervisor ON works(supervisor_id);
CREATE INDEX idx_works_status ON works(status);
