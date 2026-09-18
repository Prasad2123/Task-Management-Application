-- Migration: V18__create_companies_master.sql
-- Description: Companies master table and seed data for field service testing.

CREATE TABLE IF NOT EXISTS companies (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL UNIQUE,
    address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed initial test companies with identical testing address and coordinates
INSERT INTO companies (company_name, address, latitude, longitude) VALUES
    ('Company A', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company B', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company C', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company D', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company E', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423)
ON CONFLICT (company_name) DO UPDATE SET
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;
