-- V16: Master tasks catalog, work checklist items enhancement, additional works enhancement

CREATE TABLE IF NOT EXISTS master_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_label VARCHAR(200) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed predefined/common master tasks
INSERT INTO master_tasks (task_label, category, display_order) VALUES
    ('General Site Inspection', 'INSPECTION', 1),
    ('Pest Control Treatment', 'TREATMENT', 2),
    ('Equipment Inspection', 'INSPECTION', 3),
    ('Preventive Maintenance Check', 'MAINTENANCE', 4),
    ('Safety Inspection', 'SAFETY', 5),
    ('Area Cleaning', 'CLEANING', 6),
    ('Electrical Inspection', 'ELECTRICAL', 7),
    ('HVAC Inspection', 'HVAC', 8),
    ('Equipment Cleaning', 'CLEANING', 9),
    ('Deep Cleaning', 'CLEANING', 10),
    ('Additional Pest Treatment', 'TREATMENT', 11),
    ('Equipment Repair', 'REPAIR', 12),
    ('Additional Area Inspection', 'INSPECTION', 13)
ON CONFLICT (task_label) DO NOTHING;

ALTER TABLE work_checklist_items
    ADD COLUMN IF NOT EXISTS master_task_id BIGINT REFERENCES master_tasks(id),
    ADD COLUMN IF NOT EXISTS task_label VARCHAR(200);

UPDATE work_checklist_items
SET task_label = title
WHERE task_label IS NULL;

UPDATE work_checklist_items wci
SET master_task_id = mt.id
FROM master_tasks mt
WHERE mt.task_label = wci.task_label AND wci.master_task_id IS NULL;

ALTER TABLE additional_works
    ADD COLUMN IF NOT EXISTS master_task_id BIGINT REFERENCES master_tasks(id),
    ADD COLUMN IF NOT EXISTS task_label VARCHAR(200);

UPDATE additional_works
SET task_label = description
WHERE task_label IS NULL;

UPDATE additional_works aw
SET master_task_id = mt.id
FROM master_tasks mt
WHERE mt.task_label = aw.task_label AND aw.master_task_id IS NULL;
