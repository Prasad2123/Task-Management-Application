-- ====================================================================
-- FIELD SERVICE MANAGEMENT APPLICATION
-- COMPLETE, SAFE, IDEMPOTENT SUPABASE SETUP SCRIPT
-- Destination: supabase/migrations/20260914_supabase_complete_setup.sql
-- Project: https://ajgkqjemiqsyqirainok.supabase.co
-- Generated: 2026-09-14
--
-- DESIGNED FOR SINGLE-CLICK EXECUTION IN SUPABASE SQL EDITOR
-- Safe for initial setup AND safe to re-run multiple times.
-- ====================================================================

-- ====================================================================
-- SECTION 1: EXTENSIONS
-- ====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ====================================================================
-- SECTION 2: CREATE ALL APPLICATION TABLES (IF NOT EXISTS)
-- All 9 core application tables defined before any references or seeds.
-- ====================================================================

-- 1. Table: public.users
CREATE TABLE IF NOT EXISTS public.users (
    id BIGSERIAL PRIMARY KEY,
    auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL CHECK (role IN ('SERVICE_BOY', 'POC', 'SITE_SUPERVISOR')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Ensure columns and non-null flexibility if users table existed previously
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'password_hash' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.users ALTER COLUMN password_hash DROP NOT NULL;
    END IF;
END $$;

-- 2. Table: public.works
CREATE TABLE IF NOT EXISTS public.works (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    work_type VARCHAR(100),
    description TEXT,
    notes VARCHAR(100),
    scheduled_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED'
        CHECK (status IN ('ASSIGNED','IN_PROGRESS','SUBMITTED_FOR_REVIEW',
                          'POC_APPROVED','SUPERVISOR_APPROVED','REJECTED','COMPLETED')),
    service_boy_id BIGINT NOT NULL REFERENCES public.users(id),
    poc_id BIGINT NOT NULL REFERENCES public.users(id),
    supervisor_id BIGINT NOT NULL REFERENCES public.users(id),
    company_name VARCHAR(200),
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    allowed_radius_meters DOUBLE PRECISION DEFAULT 150.0,
    start_time TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.works ADD COLUMN IF NOT EXISTS allowed_radius_meters DOUBLE PRECISION DEFAULT 150.0;
ALTER TABLE public.works ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE public.works ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE public.works ADD COLUMN IF NOT EXISTS start_time TIMESTAMPTZ;
ALTER TABLE public.works ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE public.works ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- 3. Table: public.work_checklist_items
CREATE TABLE IF NOT EXISTS public.work_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    is_additional BOOLEAN NOT NULL DEFAULT FALSE,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    completed_by_id BIGINT REFERENCES public.users(id),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Table: public.additional_works
CREATE TABLE IF NOT EXISTS public.additional_works (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    description VARCHAR(300) NOT NULL,
    client_item_id VARCHAR(64),
    created_by_id BIGINT NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Table: public.work_photos
CREATE TABLE IF NOT EXISTS public.work_photos (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL'
        CHECK (category IN ('SITE_INSPECTION','TREATMENT_APPLICATION','EQUIPMENT_CHECK',
                            'SAFETY_PPE','ADDITIONAL_WORK','GENERAL')),
    caption TEXT,
    uploaded_by_id BIGINT NOT NULL REFERENCES public.users(id),
    storage_reference TEXT,
    upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (upload_status IN ('PENDING','UPLOADING','UPLOADED','FAILED')),
    file_name VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    photo_url TEXT,
    client_photo_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Table: public.approvals
CREATE TABLE IF NOT EXISTS public.approvals (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    approver_id BIGINT NOT NULL REFERENCES public.users(id),
    approver_role VARCHAR(20) NOT NULL CHECK (approver_role IN ('POC', 'SITE_SUPERVISOR')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    decided_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_work_role UNIQUE (work_id, approver_role)
);

-- Ensure uq_approval_work_role unique constraint exists even if table was previously created without it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_approval_work_role'
    ) THEN
        ALTER TABLE public.approvals ADD CONSTRAINT uq_approval_work_role UNIQUE (work_id, approver_role);
    END IF;
END $$;

-- 7. Table: public.activity_events
CREATE TABLE IF NOT EXISTS public.activity_events (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    performed_by_id BIGINT REFERENCES public.users(id),
    event_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy_meters DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Table: public.notifications
CREATE TABLE IF NOT EXISTS public.notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    work_id BIGINT REFERENCES public.works(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'STORED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. Table: public.work_reports
CREATE TABLE IF NOT EXISTS public.work_reports (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    report_number VARCHAR(64) NOT NULL UNIQUE,
    storage_reference VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL DEFAULT 'application/pdf',
    file_size BIGINT NOT NULL DEFAULT 0,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by BIGINT REFERENCES public.users(id),
    version INT NOT NULL DEFAULT 1
);

-- Ensure report_number unique constraint exists if pre-created without it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_work_reports_number'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public' AND table_name = 'work_reports' AND constraint_type = 'UNIQUE'
    ) THEN
        ALTER TABLE public.work_reports ADD CONSTRAINT uq_work_reports_number UNIQUE (report_number);
    END IF;
END $$;

-- ====================================================================
-- SECTION 3: PERFORMANCE INDEXES
-- ====================================================================
CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
CREATE INDEX IF NOT EXISTS idx_users_auth_id ON public.users(auth_user_id);

CREATE INDEX IF NOT EXISTS idx_works_service_boy ON public.works(service_boy_id);
CREATE INDEX IF NOT EXISTS idx_works_poc ON public.works(poc_id);
CREATE INDEX IF NOT EXISTS idx_works_supervisor ON public.works(supervisor_id);
CREATE INDEX IF NOT EXISTS idx_works_status ON public.works(status);

CREATE INDEX IF NOT EXISTS idx_checklist_work ON public.work_checklist_items(work_id);
CREATE INDEX IF NOT EXISTS idx_checklist_additional ON public.work_checklist_items(work_id, is_additional);

CREATE INDEX IF NOT EXISTS idx_additional_work_work ON public.additional_works(work_id);
CREATE INDEX IF NOT EXISTS idx_additional_works_client_item ON public.additional_works(work_id, client_item_id);

CREATE INDEX IF NOT EXISTS idx_photos_work ON public.work_photos(work_id);
CREATE INDEX IF NOT EXISTS idx_work_photos_client_photo ON public.work_photos(work_id, client_photo_id);

CREATE INDEX IF NOT EXISTS idx_approvals_work ON public.approvals(work_id);

CREATE INDEX IF NOT EXISTS idx_activity_work ON public.activity_events(work_id);
CREATE INDEX IF NOT EXISTS idx_activity_timestamp ON public.activity_events(event_timestamp);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_work ON public.notifications(work_id);

CREATE INDEX IF NOT EXISTS idx_work_reports_work_id ON public.work_reports(work_id);

-- ====================================================================
-- SECTION 4: STORAGE BUCKETS (PRIVATE)
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'storage' AND table_name = 'buckets') THEN
        INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
        VALUES 
            ('work-photos', 'work-photos', false, 20971520, ARRAY['image/jpeg', 'image/png', 'image/webp']),
            ('work-reports', 'work-reports', false, 52428800, ARRAY['application/pdf'])
        ON CONFLICT (id) DO UPDATE SET
            public = false,
            file_size_limit = EXCLUDED.file_size_limit,
            allowed_mime_types = EXCLUDED.allowed_mime_types;
    END IF;
END $$;

-- ====================================================================
-- SECTION 5: ROW LEVEL SECURITY (RLS) & ACCESS POLICIES
-- ====================================================================
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.works ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.work_checklist_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.additional_works ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.work_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.work_reports ENABLE ROW LEVEL SECURITY;

-- Helper Function: Get caller user ID from auth.uid()
CREATE OR REPLACE FUNCTION public.get_auth_user_id()
RETURNS BIGINT
LANGUAGE sql STABLE SECURITY DEFINER
AS $$
  SELECT id FROM public.users WHERE auth_user_id = auth.uid() LIMIT 1;
$$;

-- RLS: public.users
DROP POLICY IF EXISTS users_read_authenticated ON public.users;
CREATE POLICY users_read_authenticated ON public.users
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS users_update_self ON public.users;
CREATE POLICY users_update_self ON public.users
    FOR UPDATE TO authenticated
    USING (auth_user_id = auth.uid());

-- RLS: public.works
DROP POLICY IF EXISTS works_stakeholders_read ON public.works;
CREATE POLICY works_stakeholders_read ON public.works
    FOR SELECT TO authenticated
    USING (
        service_boy_id = public.get_auth_user_id()
        OR poc_id = public.get_auth_user_id()
        OR supervisor_id = public.get_auth_user_id()
    );

DROP POLICY IF EXISTS works_stakeholders_update ON public.works;
CREATE POLICY works_stakeholders_update ON public.works
    FOR UPDATE TO authenticated
    USING (
        service_boy_id = public.get_auth_user_id()
        OR poc_id = public.get_auth_user_id()
        OR supervisor_id = public.get_auth_user_id()
    );

-- RLS: public.work_checklist_items
DROP POLICY IF EXISTS checklist_read ON public.work_checklist_items;
CREATE POLICY checklist_read ON public.work_checklist_items
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

DROP POLICY IF EXISTS checklist_service_boy_modify ON public.work_checklist_items;
CREATE POLICY checklist_service_boy_modify ON public.work_checklist_items
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id AND w.service_boy_id = public.get_auth_user_id()
        )
    );

-- RLS: public.additional_works
DROP POLICY IF EXISTS additional_works_read ON public.additional_works;
CREATE POLICY additional_works_read ON public.additional_works
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

DROP POLICY IF EXISTS additional_works_service_boy_modify ON public.additional_works;
CREATE POLICY additional_works_service_boy_modify ON public.additional_works
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id AND w.service_boy_id = public.get_auth_user_id()
        )
    );

-- RLS: public.work_photos
DROP POLICY IF EXISTS photos_read ON public.work_photos;
CREATE POLICY photos_read ON public.work_photos
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

DROP POLICY IF EXISTS photos_service_boy_modify ON public.work_photos;
CREATE POLICY photos_service_boy_modify ON public.work_photos
    FOR ALL TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id AND w.service_boy_id = public.get_auth_user_id()
        )
    );

-- RLS: public.approvals
DROP POLICY IF EXISTS approvals_read ON public.approvals;
CREATE POLICY approvals_read ON public.approvals
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

-- RLS: public.activity_events
DROP POLICY IF EXISTS activity_read ON public.activity_events;
CREATE POLICY activity_read ON public.activity_events
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

-- RLS: public.notifications
DROP POLICY IF EXISTS notifications_read_own ON public.notifications;
CREATE POLICY notifications_read_own ON public.notifications
    FOR SELECT TO authenticated
    USING (user_id = public.get_auth_user_id());

DROP POLICY IF EXISTS notifications_update_own ON public.notifications;
CREATE POLICY notifications_update_own ON public.notifications
    FOR UPDATE TO authenticated
    USING (user_id = public.get_auth_user_id());

-- RLS: public.work_reports
DROP POLICY IF EXISTS reports_read ON public.work_reports;
CREATE POLICY reports_read ON public.work_reports
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id())
        )
    );

-- Storage RLS on storage.objects
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'storage' AND table_name = 'objects') THEN
        DROP POLICY IF EXISTS "Photos upload access" ON storage.objects;
        CREATE POLICY "Photos upload access" ON storage.objects
            FOR INSERT TO authenticated
            WITH CHECK (bucket_id = 'work-photos');

        DROP POLICY IF EXISTS "Photos read access" ON storage.objects;
        CREATE POLICY "Photos read access" ON storage.objects
            FOR SELECT TO authenticated
            USING (bucket_id = 'work-photos' OR bucket_id = 'work-reports');

        DROP POLICY IF EXISTS "Reports upload access" ON storage.objects;
        CREATE POLICY "Reports upload access" ON storage.objects
            FOR INSERT TO authenticated
            WITH CHECK (bucket_id = 'work-reports');
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- ====================================================================
-- SECTION 6: SERVER-AUTHORITATIVE ATOMIC RPC FUNCTIONS
-- ====================================================================

-- 1. Get Current User Profile
CREATE OR REPLACE FUNCTION public.get_current_user_profile()
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_user public.users%ROWTYPE;
BEGIN
    SELECT * INTO v_user FROM public.users WHERE auth_user_id = auth.uid() LIMIT 1;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'User profile not found for authenticated account';
    END IF;
    RETURN to_jsonb(v_user);
END;
$$;

-- 2. Start Work (with authoritative Haversine GPS Geofence validation)
CREATE OR REPLACE FUNCTION public.start_work(
    p_work_id BIGINT,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_accuracy_meters DOUBLE PRECISION DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_distance DOUBLE PRECISION;
    v_allowed_radius DOUBLE PRECISION;
    v_dlat DOUBLE PRECISION;
    v_dlng DOUBLE PRECISION;
    v_a DOUBLE PRECISION;
    v_c DOUBLE PRECISION;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    -- Validate caller
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();
    
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: Caller user profile not found';
    END IF;

    IF v_caller_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Access Denied: Only SERVICE_BOY can start work';
    END IF;

    -- Fetch and lock work
    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.service_boy_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    IF v_work.status != 'ASSIGNED' THEN
        RAISE EXCEPTION 'Work cannot be started. Current status: %', v_work.status;
    END IF;

    -- Validate GPS coordinates provided
    IF p_latitude IS NULL OR p_longitude IS NULL OR p_latitude < -90 OR p_latitude > 90 OR p_longitude < -180 OR p_longitude > 180 THEN
        RAISE EXCEPTION 'Invalid GPS coordinates provided';
    END IF;

    -- Authoritative work location check
    IF v_work.latitude IS NULL OR v_work.longitude IS NULL THEN
        RAISE EXCEPTION 'Authoritative work site location is not configured for this work';
    END IF;

    -- Haversine Distance Calculation (Earth radius = 6,371,000 meters)
    v_dlat := RADIANS(p_latitude - v_work.latitude);
    v_dlng := RADIANS(p_longitude - v_work.longitude);
    v_a := POWER(SIN(v_dlat / 2.0), 2) + COS(RADIANS(v_work.latitude)) * COS(RADIANS(p_latitude)) * POWER(SIN(v_dlng / 2.0), 2);
    v_c := 2.0 * ATAN2(SQRT(v_a), SQRT(1.0 - v_a));
    v_distance := 6371000.0 * v_c;

    v_allowed_radius := COALESCE(v_work.allowed_radius_meters, 150.0);

    IF v_distance > v_allowed_radius THEN
        RAISE EXCEPTION 'Outside permitted work location. Distance: %m, Allowed radius: %m', ROUND(v_distance), ROUND(v_allowed_radius);
    END IF;

    -- Transition state
    UPDATE public.works
    SET status = 'IN_PROGRESS',
        start_time = v_now,
        updated_at = v_now
    WHERE id = p_work_id
    RETURNING * INTO v_work;

    -- Record activity event
    INSERT INTO public.activity_events (
        work_id, event_type, description, performed_by_id, event_timestamp, latitude, longitude, accuracy_meters
    ) VALUES (
        p_work_id,
        'WORK_STARTED',
        FORMAT('Work started at verified location (%.4f, %.4f, distance: %sm) by %s', p_latitude, p_longitude, ROUND(v_distance), v_caller_name),
        v_caller_id,
        v_now,
        p_latitude,
        p_longitude,
        p_accuracy_meters
    );

    RETURN to_jsonb(v_work);
END;
$$;

-- 3. Submit For Review
CREATE OR REPLACE FUNCTION public.submit_for_review(p_work_id BIGINT)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Access Denied: Only SERVICE_BOY can submit work for review';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.service_boy_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    IF v_work.status != 'IN_PROGRESS' THEN
        RAISE EXCEPTION 'Work must be IN_PROGRESS to submit for review. Current: %', v_work.status;
    END IF;

    UPDATE public.works
    SET status = 'SUBMITTED_FOR_REVIEW',
        submitted_at = v_now,
        updated_at = v_now
    WHERE id = p_work_id
    RETURNING * INTO v_work;

    -- Reset previous approvals to PENDING if re-submitting after rework
    UPDATE public.approvals
    SET status = 'PENDING', decided_at = NULL, rejection_reason = NULL, updated_at = v_now
    WHERE work_id = p_work_id;

    -- Record Activity
    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (p_work_id, 'WORK_SUBMITTED', FORMAT('Work submitted for review by %s', v_caller_name), v_caller_id, v_now);

    -- Notify POC
    INSERT INTO public.notifications (user_id, work_id, type, title, message)
    VALUES (
        v_work.poc_id,
        p_work_id,
        'WORK_SUBMITTED',
        'Work Submitted for Review',
        FORMAT('Service Boy %s submitted %s for your review.', v_caller_name, v_work.title)
    );

    RETURN to_jsonb(v_work);
END;
$$;

-- 4. POC Decision (Approve or Reject with mandatory reason)
CREATE OR REPLACE FUNCTION public.poc_decision(
    p_work_id BIGINT,
    p_decision VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_approval public.approvals%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'POC' THEN
        RAISE EXCEPTION 'Access Denied: Only POC can make POC decision';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.poc_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not the assigned POC for this work';
    END IF;

    IF v_work.status != 'SUBMITTED_FOR_REVIEW' THEN
        RAISE EXCEPTION 'Work must be in SUBMITTED_FOR_REVIEW status. Current: %', v_work.status;
    END IF;

    IF UPPER(p_decision) = 'APPROVED' THEN
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, updated_at)
        VALUES (p_work_id, v_caller_id, 'POC', 'APPROVED', v_now, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'APPROVED', decided_at = v_now, rejection_reason = NULL, updated_at = v_now
        RETURNING * INTO v_approval;

        UPDATE public.works SET status = 'POC_APPROVED', updated_at = v_now WHERE id = p_work_id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'POC_APPROVED', FORMAT('Work approved by POC: %s', v_caller_name), v_caller_id, v_now);

        -- Notify Service Boy
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'POC_APPROVED', 'Work Evidence Approved',
            FORMAT('%s approved the work evidence for %s. Awaiting final Supervisor sign-off.', v_caller_name, v_work.title)
        );

        -- Notify Supervisor
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.supervisor_id, p_work_id, 'POC_APPROVED', FORMAT('Review Required: %s', v_work.title),
            FORMAT('%s has approved work evidence. Please complete supervisor review.', v_caller_name)
        );

    ELSIF UPPER(p_decision) = 'REJECTED' THEN
        IF p_reason IS NULL OR TRIM(p_reason) = '' THEN
            RAISE EXCEPTION 'Rejection requires a mandatory non-empty reason';
        END IF;

        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, rejection_reason, updated_at)
        VALUES (p_work_id, v_caller_id, 'POC', 'REJECTED', v_now, TRIM(p_reason), v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'REJECTED', decided_at = v_now, rejection_reason = TRIM(p_reason), updated_at = v_now
        RETURNING * INTO v_approval;

        UPDATE public.works SET status = 'REJECTED', updated_at = v_now WHERE id = p_work_id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'POC_REJECTED', FORMAT('Work rejected by POC: %s - %s', v_caller_name, TRIM(p_reason)), v_caller_id, v_now);

        -- Notify Service Boy
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'POC_REJECTED', 'Changes Requested by POC',
            FORMAT('%s requested revisions on %s: "%s"', v_caller_name, v_work.title, TRIM(p_reason))
        );

    ELSE
        RAISE EXCEPTION 'Invalid decision: %. Must be APPROVED or REJECTED', p_decision;
    END IF;

    RETURN to_jsonb(v_approval);
END;
$$;

-- 5. Supervisor Decision (CRITICAL RULE: POC MUST HAVE APPROVED FIRST)
CREATE OR REPLACE FUNCTION public.supervisor_decision(
    p_work_id BIGINT,
    p_decision VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_poc_approval public.approvals%ROWTYPE;
    v_approval public.approvals%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'SITE_SUPERVISOR' THEN
        RAISE EXCEPTION 'Access Denied: Only SITE_SUPERVISOR can make Supervisor decision';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.supervisor_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not the assigned Supervisor for this work';
    END IF;

    -- CRITICAL BUSINESS RULE: Verify POC approval exists and is APPROVED
    SELECT * INTO v_poc_approval FROM public.approvals
    WHERE work_id = p_work_id AND approver_role = 'POC';

    IF v_poc_approval.status IS NULL OR v_poc_approval.status != 'APPROVED' THEN
        RAISE EXCEPTION 'Conflict: POC must approve first before Supervisor review. Current POC status: %', COALESCE(v_poc_approval.status, 'NONE');
    END IF;

    IF UPPER(p_decision) = 'APPROVED' THEN
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, updated_at)
        VALUES (p_work_id, v_caller_id, 'SITE_SUPERVISOR', 'APPROVED', v_now, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'APPROVED', decided_at = v_now, rejection_reason = NULL, updated_at = v_now
        RETURNING * INTO v_approval;

        UPDATE public.works SET status = 'SUPERVISOR_APPROVED', updated_at = v_now WHERE id = p_work_id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'SUPERVISOR_APPROVED', FORMAT('Work approved by Supervisor: %s', v_caller_name), v_caller_id, v_now);

        -- Notify Service Boy: Job ready for completion
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'SUPERVISOR_APPROVED', 'Work Approved — Ready for Completion',
            FORMAT('Supervisor %s gave final approval for %s. You may now complete the job.', v_caller_name, v_work.title)
        );

        -- Notify POC
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.poc_id, p_work_id, 'SUPERVISOR_APPROVED', 'Supervisor Review Completed',
            FORMAT('Supervisor %s gave final approval for %s.', v_caller_name, v_work.title)
        );

    ELSIF UPPER(p_decision) = 'REJECTED' THEN
        IF p_reason IS NULL OR TRIM(p_reason) = '' THEN
            RAISE EXCEPTION 'Rejection requires a mandatory non-empty reason';
        END IF;

        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, rejection_reason, updated_at)
        VALUES (p_work_id, v_caller_id, 'SITE_SUPERVISOR', 'REJECTED', v_now, TRIM(p_reason), v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'REJECTED', decided_at = v_now, rejection_reason = TRIM(p_reason), updated_at = v_now
        RETURNING * INTO v_approval;

        UPDATE public.works SET status = 'REJECTED', updated_at = v_now WHERE id = p_work_id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'SUPERVISOR_REJECTED', FORMAT('Work rejected by Supervisor: %s - %s', v_caller_name, TRIM(p_reason)), v_caller_id, v_now);

        -- Notify Service Boy
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'SUPERVISOR_REJECTED', 'Changes Requested by Supervisor',
            FORMAT('Supervisor %s requested revisions on %s: "%s"', v_caller_name, v_work.title, TRIM(p_reason))
        );

    ELSE
        RAISE EXCEPTION 'Invalid decision: %. Must be APPROVED or REJECTED', p_decision;
    END IF;

    RETURN to_jsonb(v_approval);
END;
$$;

-- 6. Complete Work (Idempotent, Server-Authoritative)
CREATE OR REPLACE FUNCTION public.complete_work(p_work_id BIGINT)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_poc_status VARCHAR;
    v_sup_status VARCHAR;
    v_now TIMESTAMPTZ := NOW();
    v_report_number VARCHAR(64);
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Access Denied: Only SERVICE_BOY can complete work';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.service_boy_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    -- Idempotency Check
    IF v_work.status = 'COMPLETED' THEN
        RETURN to_jsonb(v_work);
    END IF;

    -- Verify Dual Approvals
    SELECT status INTO v_poc_status FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'POC';
    SELECT status INTO v_sup_status FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'SITE_SUPERVISOR';

    IF v_poc_status != 'APPROVED' OR v_sup_status != 'APPROVED' THEN
        RAISE EXCEPTION 'Work requires both POC and Supervisor approval before completion. Current: POC=%, Supervisor=%',
            COALESCE(v_poc_status, 'NONE'), COALESCE(v_sup_status, 'NONE');
    END IF;

    UPDATE public.works
    SET status = 'COMPLETED',
        completed_at = v_now,
        updated_at = v_now
    WHERE id = p_work_id
    RETURNING * INTO v_work;

    -- Record Activity Event
    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (p_work_id, 'WORK_COMPLETED', FORMAT('Work completed by %s', v_caller_name), v_caller_id, v_now);

    -- Authoritative Work Report Record
    v_report_number := FORMAT('REP-W%s-%s', p_work_id, EXTRACT(EPOCH FROM v_now)::BIGINT);
    INSERT INTO public.work_reports (
        work_id, report_number, storage_reference, file_name, content_type, file_size, generated_at, created_by, version
    ) VALUES (
        p_work_id,
        v_report_number,
        FORMAT('work-reports/WorkReport_%s.pdf', p_work_id),
        FORMAT('WorkReport_%s.pdf', p_work_id),
        'application/pdf',
        0,
        v_now,
        v_caller_id,
        1
    ) ON CONFLICT (report_number) DO NOTHING;

    -- Notify All 3 Stakeholders
    INSERT INTO public.notifications (user_id, work_id, type, title, message) VALUES
        (v_work.service_boy_id, p_work_id, 'WORK_COMPLETED', 'Work Completed', FORMAT('Work completed — final report is available for %s.', v_work.title)),
        (v_work.poc_id, p_work_id, 'WORK_COMPLETED', 'Work Completed', FORMAT('Work completed — final report is available for %s.', v_work.title)),
        (v_work.supervisor_id, p_work_id, 'WORK_COMPLETED', 'Work Completed', FORMAT('Work completed — final report is available for %s.', v_work.title));

    RETURN to_jsonb(v_work);
END;
$$;

-- 7. Resume Work (After Rejection)
CREATE OR REPLACE FUNCTION public.resume_work(p_work_id BIGINT)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Access Denied: Only SERVICE_BOY can resume work';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.service_boy_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    IF v_work.status != 'REJECTED' THEN
        RAISE EXCEPTION 'Work is not in REJECTED state. Current: %', v_work.status;
    END IF;

    UPDATE public.works
    SET status = 'IN_PROGRESS',
        updated_at = v_now
    WHERE id = p_work_id
    RETURNING * INTO v_work;

    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (p_work_id, 'WORK_RESUMED', FORMAT('Work resumed after rejection by %s', v_caller_name), v_caller_id, v_now);

    INSERT INTO public.notifications (user_id, work_id, type, title, message)
    VALUES (
        v_work.poc_id, p_work_id, 'WORK_RESUMED', 'Work Resumed',
        FORMAT('Service Boy %s has resumed work on %s to address review feedback.', v_caller_name, v_work.title)
    );

    RETURN to_jsonb(v_work);
END;
$$;

-- 8. Get My Works (Role-Filtered)
CREATE OR REPLACE FUNCTION public.get_my_works()
RETURNS SETOF public.works
LANGUAGE plpgsql STABLE SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
BEGIN
    SELECT id, role INTO v_caller_id, v_caller_role
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_id IS NULL THEN
        RETURN;
    END IF;

    IF v_caller_role = 'SERVICE_BOY' THEN
        RETURN QUERY SELECT * FROM public.works WHERE service_boy_id = v_caller_id ORDER BY id ASC;
    ELSIF v_caller_role = 'POC' THEN
        RETURN QUERY SELECT * FROM public.works WHERE poc_id = v_caller_id ORDER BY id ASC;
    ELSIF v_caller_role = 'SITE_SUPERVISOR' THEN
        RETURN QUERY SELECT * FROM public.works WHERE supervisor_id = v_caller_id ORDER BY id ASC;
    END IF;
END;
$$;

-- ====================================================================
-- SECTION 7: DEMO AUTH USERS PROVISIONING (Supabase Auth)
-- Safe, procedural provisioning: service@demo.com, poc@demo.com, supervisor@demo.com
-- Password: demo1234
-- ====================================================================
DO $$
DECLARE
    v_service_id UUID;
    v_poc_id UUID;
    v_supervisor_id UUID;
BEGIN
    -- 1. service@demo.com
    SELECT id INTO v_service_id FROM auth.users WHERE email = 'service@demo.com' LIMIT 1;
    IF v_service_id IS NULL THEN
        v_service_id := '11111111-1111-1111-1111-111111111111'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change,
            email_change_token_current, phone_change, phone_change_token, reauthentication_token
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_service_id,
            'authenticated',
            'authenticated',
            'service@demo.com',
            crypt('demo1234', gen_salt('bf', 10)),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Rahul Patil","role":"SERVICE_BOY"}'::jsonb,
            NOW(),
            NOW(),
            '', '', '', '', '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf', 10)),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Rahul Patil","role":"SERVICE_BOY"}'::jsonb,
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change_token_new = COALESCE(email_change_token_new, ''),
            email_change = COALESCE(email_change, ''),
            email_change_token_current = COALESCE(email_change_token_current, ''),
            phone_change = COALESCE(phone_change, ''),
            phone_change_token = COALESCE(phone_change_token, ''),
            reauthentication_token = COALESCE(reauthentication_token, ''),
            updated_at = NOW()
        WHERE id = v_service_id;
    END IF;

    -- Link identity for service@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_service_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_service_id,
            jsonb_build_object('sub', v_service_id::text, 'email', 'service@demo.com', 'email_verified', true, 'phone_verified', false),
            'email', v_service_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = jsonb_build_object('sub', v_service_id::text, 'email', 'service@demo.com', 'email_verified', true, 'phone_verified', false),
            provider_id = v_service_id::text,
            updated_at = NOW()
        WHERE user_id = v_service_id AND provider = 'email';
    END IF;

    -- 2. poc@demo.com
    SELECT id INTO v_poc_id FROM auth.users WHERE email = 'poc@demo.com' LIMIT 1;
    IF v_poc_id IS NULL THEN
        v_poc_id := '22222222-2222-2222-2222-222222222222'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change,
            email_change_token_current, phone_change, phone_change_token, reauthentication_token
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_poc_id,
            'authenticated',
            'authenticated',
            'poc@demo.com',
            crypt('demo1234', gen_salt('bf', 10)),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Amit Sharma","role":"POC"}'::jsonb,
            NOW(),
            NOW(),
            '', '', '', '', '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf', 10)),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Amit Sharma","role":"POC"}'::jsonb,
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change_token_new = COALESCE(email_change_token_new, ''),
            email_change = COALESCE(email_change, ''),
            email_change_token_current = COALESCE(email_change_token_current, ''),
            phone_change = COALESCE(phone_change, ''),
            phone_change_token = COALESCE(phone_change_token, ''),
            reauthentication_token = COALESCE(reauthentication_token, ''),
            updated_at = NOW()
        WHERE id = v_poc_id;
    END IF;

    -- Link identity for poc@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_poc_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_poc_id,
            jsonb_build_object('sub', v_poc_id::text, 'email', 'poc@demo.com', 'email_verified', true, 'phone_verified', false),
            'email', v_poc_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = jsonb_build_object('sub', v_poc_id::text, 'email', 'poc@demo.com', 'email_verified', true, 'phone_verified', false),
            provider_id = v_poc_id::text,
            updated_at = NOW()
        WHERE user_id = v_poc_id AND provider = 'email';
    END IF;

    -- 3. supervisor@demo.com
    SELECT id INTO v_supervisor_id FROM auth.users WHERE email = 'supervisor@demo.com' LIMIT 1;
    IF v_supervisor_id IS NULL THEN
        v_supervisor_id := '33333333-3333-3333-3333-333333333333'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change,
            email_change_token_current, phone_change, phone_change_token, reauthentication_token
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_supervisor_id,
            'authenticated',
            'authenticated',
            'supervisor@demo.com',
            crypt('demo1234', gen_salt('bf', 10)),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Suresh Patil","role":"SITE_SUPERVISOR"}'::jsonb,
            NOW(),
            NOW(),
            '', '', '', '', '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf', 10)),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Suresh Patil","role":"SITE_SUPERVISOR"}'::jsonb,
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change_token_new = COALESCE(email_change_token_new, ''),
            email_change = COALESCE(email_change, ''),
            email_change_token_current = COALESCE(email_change_token_current, ''),
            phone_change = COALESCE(phone_change, ''),
            phone_change_token = COALESCE(phone_change_token, ''),
            reauthentication_token = COALESCE(reauthentication_token, ''),
            updated_at = NOW()
        WHERE id = v_supervisor_id;
    END IF;

    -- Link identity for supervisor@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_supervisor_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_supervisor_id,
            jsonb_build_object('sub', v_supervisor_id::text, 'email', 'supervisor@demo.com', 'email_verified', true, 'phone_verified', false),
            'email', v_supervisor_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = jsonb_build_object('sub', v_supervisor_id::text, 'email', 'supervisor@demo.com', 'email_verified', true, 'phone_verified', false),
            provider_id = v_supervisor_id::text,
            updated_at = NOW()
        WHERE user_id = v_supervisor_id AND provider = 'email';
    END IF;
END $$;

-- ====================================================================
-- SECTION 8: APPLICATION USER PROFILES (public.users)
-- Safe, procedural upsert by unique email without assuming fixed sequence IDs.
-- ====================================================================
DO $$
DECLARE
    v_svc_auth UUID;
    v_poc_auth UUID;
    v_sup_auth UUID;
BEGIN
    SELECT id INTO v_svc_auth FROM auth.users WHERE email = 'service@demo.com' LIMIT 1;
    SELECT id INTO v_poc_auth FROM auth.users WHERE email = 'poc@demo.com' LIMIT 1;
    SELECT id INTO v_sup_auth FROM auth.users WHERE email = 'supervisor@demo.com' LIMIT 1;

    -- Service Boy profile
    IF EXISTS (SELECT 1 FROM public.users WHERE email = 'service@demo.com') THEN
        UPDATE public.users
        SET auth_user_id = v_svc_auth,
            name = 'Rahul Patil',
            phone = '+91 98765 43210',
            role = 'SERVICE_BOY',
            is_active = true,
            updated_at = NOW()
        WHERE email = 'service@demo.com';
    ELSE
        INSERT INTO public.users (auth_user_id, name, email, phone, role, is_active)
        VALUES (v_svc_auth, 'Rahul Patil', 'service@demo.com', '+91 98765 43210', 'SERVICE_BOY', true);
    END IF;

    -- POC profile
    IF EXISTS (SELECT 1 FROM public.users WHERE email = 'poc@demo.com') THEN
        UPDATE public.users
        SET auth_user_id = v_poc_auth,
            name = 'Amit Sharma',
            phone = '+91 98765 43211',
            role = 'POC',
            is_active = true,
            updated_at = NOW()
        WHERE email = 'poc@demo.com';
    ELSE
        INSERT INTO public.users (auth_user_id, name, email, phone, role, is_active)
        VALUES (v_poc_auth, 'Amit Sharma', 'poc@demo.com', '+91 98765 43211', 'POC', true);
    END IF;

    -- Supervisor profile
    IF EXISTS (SELECT 1 FROM public.users WHERE email = 'supervisor@demo.com') THEN
        UPDATE public.users
        SET auth_user_id = v_sup_auth,
            name = 'Suresh Patil',
            phone = '+91 98765 43212',
            role = 'SITE_SUPERVISOR',
            is_active = true,
            updated_at = NOW()
        WHERE email = 'supervisor@demo.com';
    ELSE
        INSERT INTO public.users (auth_user_id, name, email, phone, role, is_active)
        VALUES (v_sup_auth, 'Suresh Patil', 'supervisor@demo.com', '+91 98765 43212', 'SITE_SUPERVISOR', true);
    END IF;
END $$;

-- ====================================================================
-- SECTION 9: WORKS & SEED DATA
-- Safe, procedural creation:
-- 1. Historical Work: 'Monthly Pest Control Service' (COMPLETED)
-- 2. Fresh Test Work: 'Monthly Pest Control Service - SUPABASE TEST' (ASSIGNED)
--
-- STRICT RULE ENFORCEMENT:
-- - DO NOT specify checklist IDs manually (let BIGSERIAL generate them)
-- - DO NOT specify activity IDs manually
-- - DO NOT specify notification IDs manually
-- - DO NOT specify approval IDs manually
-- - DO NOT blanket delete all public data
-- ====================================================================
DO $$
DECLARE
    v_svc_id BIGINT;
    v_poc_id BIGINT;
    v_sup_id BIGINT;
    v_hist_work_id BIGINT;
    v_test_work_id BIGINT;
BEGIN
    SELECT id INTO v_svc_id FROM public.users WHERE email = 'service@demo.com' LIMIT 1;
    SELECT id INTO v_poc_id FROM public.users WHERE email = 'poc@demo.com' LIMIT 1;
    SELECT id INTO v_sup_id FROM public.users WHERE email = 'supervisor@demo.com' LIMIT 1;

    -- ----------------------------------------------------------------
    -- 1. HISTORICAL COMPLETED WORK: 'Monthly Pest Control Service'
    -- ----------------------------------------------------------------
    SELECT id INTO v_hist_work_id FROM public.works WHERE title = 'Monthly Pest Control Service' LIMIT 1;
    IF v_hist_work_id IS NULL THEN
        INSERT INTO public.works (
            title, work_type, description, notes, scheduled_date, status,
            service_boy_id, poc_id, supervisor_id, company_name, address,
            latitude, longitude, allowed_radius_meters, start_time, submitted_at, completed_at
        ) VALUES (
            'Monthly Pest Control Service',
            'PEST_CONTROL',
            'Comprehensive monthly pest control treatment for entire office premises including pantry, cafeteria, washrooms, and workstations.',
            'Pantry and washrooms are high priority',
            CURRENT_DATE,
            'COMPLETED',
            v_svc_id, v_poc_id, v_sup_id,
            'Acme Corp - Main Office',
            'Plot 42, MIDC Industrial Area, Ratnagiri, Maharashtra 415612',
            17.5230403, 73.5378423, 150.0,
            NOW() - INTERVAL '2 hours',
            NOW() - INTERVAL '1 hour',
            NOW() - INTERVAL '30 minutes'
        ) RETURNING id INTO v_hist_work_id;

        -- Historical Checklist Items (NO EXPLICIT IDs)
        INSERT INTO public.work_checklist_items (work_id, title, description, is_completed, display_order) VALUES
            (v_hist_work_id, 'General Site Inspection', 'Inspect premises for pest activity', true, 1),
            (v_hist_work_id, 'Pest Control Treatment', 'Apply treatment in cafeteria and pantry', true, 2),
            (v_hist_work_id, 'Equipment Inspection', 'Check traps and bait stations', true, 3),
            (v_hist_work_id, 'Preventive Maintenance Check', 'Verify pest barrier seals', true, 4),
            (v_hist_work_id, 'Safety Inspection', 'Ensure all chemical safety protocols observed', true, 5),
            (v_hist_work_id, 'Area Cleaning', 'Clean and sanitize treated areas', true, 6);

        -- Historical Approvals (NO EXPLICIT IDs, matches UNIQUE(work_id, approver_role))
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at) VALUES
            (v_hist_work_id, v_poc_id, 'POC', 'APPROVED', NOW() - INTERVAL '45 minutes'),
            (v_hist_work_id, v_sup_id, 'SITE_SUPERVISOR', 'APPROVED', NOW() - INTERVAL '35 minutes')
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = EXCLUDED.status, decided_at = EXCLUDED.decided_at;

        -- Historical Report
        INSERT INTO public.work_reports (
            work_id, report_number, storage_reference, file_name, content_type, file_size, generated_at, created_by, version
        ) VALUES (
            v_hist_work_id,
            FORMAT('REP-W%s-%s', v_hist_work_id, EXTRACT(EPOCH FROM (NOW() - INTERVAL '30 minutes'))::BIGINT),
            FORMAT('work-reports/WorkReport_%s.pdf', v_hist_work_id),
            FORMAT('WorkReport_%s.pdf', v_hist_work_id),
            'application/pdf',
            1024,
            NOW() - INTERVAL '30 minutes',
            v_svc_id,
            1
        ) ON CONFLICT (report_number) DO NOTHING;
    END IF;

    -- ----------------------------------------------------------------
    -- 2. FRESH TEST WORK: 'Monthly Pest Control Service - SUPABASE TEST'
    -- Ready for physical-device end-to-end GPS start and workflow test.
    -- ----------------------------------------------------------------
    SELECT id INTO v_test_work_id FROM public.works WHERE title = 'Monthly Pest Control Service - SUPABASE TEST' LIMIT 1;
    IF v_test_work_id IS NULL THEN
        INSERT INTO public.works (
            title, work_type, description, notes, scheduled_date, status,
            service_boy_id, poc_id, supervisor_id, company_name, address,
            latitude, longitude, allowed_radius_meters
        ) VALUES (
            'Monthly Pest Control Service - SUPABASE TEST',
            'PEST_CONTROL',
            'Fresh end-to-end online Supabase verification service: inspection, barrier treatment, photo proof, dual approval, and digital completion report.',
            'Authoritative GPS Geofence: Ratnagiri premises within 150 meters',
            CURRENT_DATE,
            'ASSIGNED',
            v_svc_id, v_poc_id, v_sup_id,
            'TechPark Tower A - Ground Floor',
            'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612',
            17.5230403, 73.5378423, 150.0
        ) RETURNING id INTO v_test_work_id;
    ELSE
        -- Reset only the fresh test work to pristine ASSIGNED state
        UPDATE public.works
        SET status = 'ASSIGNED',
            work_type = 'PEST_CONTROL',
            description = 'Fresh end-to-end online Supabase verification service: inspection, barrier treatment, photo proof, dual approval, and digital completion report.',
            notes = 'Authoritative GPS Geofence: Ratnagiri premises within 150 meters',
            service_boy_id = v_svc_id,
            poc_id = v_poc_id,
            supervisor_id = v_sup_id,
            company_name = 'TechPark Tower A - Ground Floor',
            address = 'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612',
            latitude = 17.5230403,
            longitude = 73.5378423,
            allowed_radius_meters = 150.0,
            start_time = NULL,
            submitted_at = NULL,
            completed_at = NULL,
            updated_at = NOW()
        WHERE id = v_test_work_id;
    END IF;

    -- Clean up previous checklist items ONLY for the test work, then recreate 6 fresh items
    DELETE FROM public.work_checklist_items WHERE work_id = v_test_work_id;
    INSERT INTO public.work_checklist_items (work_id, title, description, is_completed, display_order) VALUES
        (v_test_work_id, 'General Site Inspection', 'Inspect perimeter and office zones for pest activity', false, 1),
        (v_test_work_id, 'Pest Control Treatment', 'Apply eco-safe pest barrier spray along walls and openings', false, 2),
        (v_test_work_id, 'Equipment Inspection', 'Check and calibrate misting and bait dispensers', false, 3),
        (v_test_work_id, 'Preventive Maintenance Check', 'Verify door sweeps, drain covers, and window mesh seals', false, 4),
        (v_test_work_id, 'Safety Inspection', 'Ensure all chemical safety protocols and PPE worn', false, 5),
        (v_test_work_id, 'Area Cleaning', 'Sanitize and clean application areas after treatment', false, 6);

    -- Approvals for test work: Both POC and SITE_SUPERVISOR in PENDING status
    DELETE FROM public.approvals WHERE work_id = v_test_work_id;
    INSERT INTO public.approvals (work_id, approver_id, approver_role, status) VALUES
        (v_test_work_id, v_poc_id, 'POC', 'PENDING'),
        (v_test_work_id, v_sup_id, 'SITE_SUPERVISOR', 'PENDING');

    -- Activity event for initial assignment
    DELETE FROM public.activity_events WHERE work_id = v_test_work_id;
    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp) VALUES
        (v_test_work_id, 'WORK_CREATED', 'Work assigned to Rahul Patil (Ready for GPS Start)', v_sup_id, NOW());

    -- Assignment notification for Service Boy
    DELETE FROM public.notifications WHERE work_id = v_test_work_id;
    INSERT INTO public.notifications (user_id, work_id, type, title, message) VALUES
        (v_svc_id, v_test_work_id, 'WORK_ASSIGNED', 'New Work Assigned', 'You have been assigned to: Monthly Pest Control Service - SUPABASE TEST');

END $$;

-- ====================================================================
-- SECTION 10: SYNCHRONIZE ALL SEQUENCES
-- Dynamically aligns sequence counters with the maximum existing ID.
-- ====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'users_id_seq') THEN
        PERFORM setval('public.users_id_seq', COALESCE((SELECT MAX(id) FROM public.users), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'works_id_seq') THEN
        PERFORM setval('public.works_id_seq', COALESCE((SELECT MAX(id) FROM public.works), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'work_checklist_items_id_seq') THEN
        PERFORM setval('public.work_checklist_items_id_seq', COALESCE((SELECT MAX(id) FROM public.work_checklist_items), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'additional_works_id_seq') THEN
        PERFORM setval('public.additional_works_id_seq', COALESCE((SELECT MAX(id) FROM public.additional_works), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'work_photos_id_seq') THEN
        PERFORM setval('public.work_photos_id_seq', COALESCE((SELECT MAX(id) FROM public.work_photos), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'approvals_id_seq') THEN
        PERFORM setval('public.approvals_id_seq', COALESCE((SELECT MAX(id) FROM public.approvals), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'activity_events_id_seq') THEN
        PERFORM setval('public.activity_events_id_seq', COALESCE((SELECT MAX(id) FROM public.activity_events), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'notifications_id_seq') THEN
        PERFORM setval('public.notifications_id_seq', COALESCE((SELECT MAX(id) FROM public.notifications), 1));
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'work_reports_id_seq') THEN
        PERFORM setval('public.work_reports_id_seq', COALESCE((SELECT MAX(id) FROM public.work_reports), 1));
    END IF;
END $$;

-- ====================================================================
-- SECTION 11: FINAL VERIFICATION QUERIES (AS REQUIRED)
-- These queries output verification results when run in Supabase SQL Editor.
-- ====================================================================

-- 1. Verify all 9 public tables exist
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
AND table_name IN (
    'users',
    'works',
    'work_checklist_items',
    'additional_works',
    'work_photos',
    'approvals',
    'activity_events',
    'notifications',
    'work_reports'
)
ORDER BY table_name;

-- 2. Verify Fresh Test Work
SELECT
    id,
    title,
    status,
    latitude,
    longitude,
    allowed_radius_meters
FROM public.works
WHERE title = 'Monthly Pest Control Service - SUPABASE TEST';

-- 3. Verify Checklist Items for Fresh Test Work
SELECT
    wci.id,
    wci.work_id,
    wci.title,
    wci.is_completed,
    wci.display_order
FROM public.work_checklist_items wci
JOIN public.works w
    ON w.id = wci.work_id
WHERE w.title = 'Monthly Pest Control Service - SUPABASE TEST'
ORDER BY wci.display_order;
