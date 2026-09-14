-- V10: Add work location verification and event coordinates
-- Allows configurable geofence radius per work and location tracking for activity events.

ALTER TABLE works
ADD COLUMN IF NOT EXISTS allowed_radius_meters DOUBLE PRECISION DEFAULT 150.0;

UPDATE works
SET allowed_radius_meters = 150.0
WHERE allowed_radius_meters IS NULL;

ALTER TABLE activity_events
ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS accuracy_meters DOUBLE PRECISION;
