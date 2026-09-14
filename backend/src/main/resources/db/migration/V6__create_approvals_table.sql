-- V6: Create approvals table
-- UNIQUE constraint on (work_id, approver_role) prevents duplicate approvals per role
CREATE TABLE approvals (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES works(id) ON DELETE CASCADE,
    approver_id BIGINT NOT NULL REFERENCES users(id),
    approver_role VARCHAR(20) NOT NULL CHECK (approver_role IN ('POC', 'SITE_SUPERVISOR')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    decided_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_work_role UNIQUE (work_id, approver_role)
);

CREATE INDEX idx_approvals_work ON approvals(work_id);
