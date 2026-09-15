-- V17: Add google_maps_link to works table for Google Maps direct navigation
ALTER TABLE works
    ADD COLUMN IF NOT EXISTS google_maps_link TEXT;
