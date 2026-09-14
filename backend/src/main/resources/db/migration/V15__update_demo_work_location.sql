-- V15: Update demo work location to exact local physical test coordinates
-- Updates the authoritative coordinates for the seeded demo work record
-- to: Latitude 17.5230403, Longitude 73.5378423 with 150m allowed radius.

UPDATE works
SET latitude = 17.5230403,
    longitude = 73.5378423,
    allowed_radius_meters = 150.0,
    updated_at = NOW()
WHERE title = 'Monthly Pest Control Service';
