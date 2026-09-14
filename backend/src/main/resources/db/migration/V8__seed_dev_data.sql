-- V8: Seed development data
-- IMPORTANT: These are DEVELOPMENT-ONLY credentials.
-- Passwords: all users use "demo1234" hashed with BCrypt (cost=10)
-- BCrypt hash for "demo1234":
-- $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH (example)
-- REGENERATE with: https://bcrypt-generator.com or backend seeder

-- Use a stable BCrypt hash for "demo1234"
INSERT INTO users (name, email, password_hash, phone, role, is_active)
VALUES
    ('Rahul Patil',
     'service@demo.com',
     '$2a$10$fl8gSmw/g/Mi13VeVD4De.5flOVW3ilBiDd2XoYq.23YHNXt0SWLC',
     '+91 98765 43210',
     'SERVICE_BOY',
     TRUE),
    ('Amit Sharma',
     'poc@demo.com',
     '$2a$10$fl8gSmw/g/Mi13VeVD4De.5flOVW3ilBiDd2XoYq.23YHNXt0SWLC',
     '+91 87654 32109',
     'POC',
     TRUE),
    ('Suresh Patil',
     'supervisor@demo.com',
     '$2a$10$fl8gSmw/g/Mi13VeVD4De.5flOVW3ilBiDd2XoYq.23YHNXt0SWLC',
     '+91 76543 21098',
     'SITE_SUPERVISOR',
     TRUE);

-- Seed one demo work record
INSERT INTO works (
    title, work_type, description, notes, scheduled_date, status,
    service_boy_id, poc_id, supervisor_id,
    company_name, address, latitude, longitude
)
VALUES (
    'Monthly Pest Control Service',
    'Pest Control',
    'Perform scheduled monthly inspection, treatment and preventive maintenance activities at the assigned location. Ensure all safety protocols are followed.',
    'Please carry the standard pest control kit. Access through Gate B.',
    CURRENT_DATE,
    'ASSIGNED',
    (SELECT id FROM users WHERE email = 'service@demo.com'),
    (SELECT id FROM users WHERE email = 'poc@demo.com'),
    (SELECT id FROM users WHERE email = 'supervisor@demo.com'),
    'ABC Industrial Services',
    'Plot No. 45, Industrial Estate, Andheri East, Mumbai - 400093',
    19.1136,
    72.8697
);

-- Seed checklist items for the demo work
INSERT INTO work_checklist_items (work_id, title, description, is_additional, display_order)
SELECT
    w.id,
    item.title,
    item.description,
    FALSE,
    item.ord
FROM works w
CROSS JOIN (VALUES
    (1, 'General Site Inspection',     'Initial inspection of common areas and perimeter for pest activity'),
    (2, 'Pest Control Treatment',      'Apply approved chemical treatment as per schedule'),
    (3, 'Equipment Inspection',        'Inspect spray pumps and safety equipment condition'),
    (4, 'Preventive Maintenance Check','Inspect bait stations and replace consumable traps'),
    (5, 'Safety Inspection',           'Verify proper PPE, warning signage and chemical storage'),
    (6, 'Area Cleaning',               'Clean treated zones and safely dispose of packaging waste')
) AS item(ord, title, description)
WHERE w.title = 'Monthly Pest Control Service';

-- Seed WORK_CREATED activity event
INSERT INTO activity_events (work_id, event_type, description, performed_by_id)
SELECT
    w.id,
    'WORK_CREATED',
    'Work assigned to ' || u.name,
    u.id
FROM works w
JOIN users u ON u.email = 'service@demo.com'
WHERE w.title = 'Monthly Pest Control Service';
