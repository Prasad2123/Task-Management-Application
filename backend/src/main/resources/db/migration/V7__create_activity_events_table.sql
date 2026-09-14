-- V7: Create activity_events table
CREATE TABLE activity_events (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES works(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    performed_by_id BIGINT REFERENCES users(id),
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_work ON activity_events(work_id);
CREATE INDEX idx_activity_timestamp ON activity_events(event_timestamp);
