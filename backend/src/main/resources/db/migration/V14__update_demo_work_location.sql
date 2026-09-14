-- V14: Update demo work location for local physical GPS testing
-- Updates the authoritative coordinates for the seeded demo work record
-- to allow local physical GPS verification on real devices.

UPDATE works
SET latitude = 19.174800,
    longitude = 72.942100,
    allowed_radius_meters = 150.0,
    updated_at = NOW()
WHERE title = 'Monthly Pest Control Service';
