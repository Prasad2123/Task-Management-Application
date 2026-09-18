-- Migration: 20260916_companies_master.sql
-- Description: Companies master table, RLS, seed data with testing address & coordinates.

CREATE TABLE IF NOT EXISTS public.companies (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL UNIQUE,
    address TEXT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed initial test companies with identical testing address and coordinates
INSERT INTO public.companies (company_name, address, latitude, longitude) VALUES
    ('Company A', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company B', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company C', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company D', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423),
    ('Company E', 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612', 17.5230403, 73.5378423)
ON CONFLICT (company_name) DO UPDATE SET
    address = EXCLUDED.address,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude;

-- Enable RLS for companies
ALTER TABLE public.companies ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS companies_read ON public.companies;
CREATE POLICY companies_read ON public.companies
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS companies_admin_all ON public.companies;
CREATE POLICY companies_admin_all ON public.companies
    FOR ALL TO authenticated
    USING (EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'));
