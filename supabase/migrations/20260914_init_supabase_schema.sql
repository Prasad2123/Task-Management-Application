-- ====================================================================
-- FIELD SERVICE MANAGEMENT APPLICATION
-- CONSOLIDATED SUPABASE DATABASE SCHEMA MIGRATION (V2 - FIX 42P10)
-- Project: https://ajgkqjemiqsyqirainok.supabase.co
-- Date: 2026-09-14
-- ====================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Create Public Users Table (Application Profiles mapped to auth.users)
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

CREATE INDEX IF NOT EXISTS idx_users_email ON public.users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
CREATE INDEX IF NOT EXISTS idx_users_auth_id ON public.users(auth_user_id);

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

-- 3. Create Works Table
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

CREATE INDEX IF NOT EXISTS idx_works_service_boy ON public.works(service_boy_id);
CREATE INDEX IF NOT EXISTS idx_works_poc ON public.works(poc_id);
CREATE INDEX IF NOT EXISTS idx_works_supervisor ON public.works(supervisor_id);
CREATE INDEX IF NOT EXISTS idx_works_status ON public.works(status);

-- 4. Create Checklist Items Table
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

CREATE INDEX IF NOT EXISTS idx_checklist_work ON public.work_checklist_items(work_id);
CREATE INDEX IF NOT EXISTS idx_checklist_additional ON public.work_checklist_items(work_id, is_additional);

-- 5. Create Additional Works Table
CREATE TABLE IF NOT EXISTS public.additional_works (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    description VARCHAR(300) NOT NULL,
    client_item_id VARCHAR(64),
    created_by_id BIGINT NOT NULL REFERENCES public.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_additional_work_work ON public.additional_works(work_id);
CREATE INDEX IF NOT EXISTS idx_additional_works_client_item ON public.additional_works(work_id, client_item_id);

-- 6. Create Work Photos Table
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

CREATE INDEX IF NOT EXISTS idx_photos_work ON public.work_photos(work_id);
CREATE INDEX IF NOT EXISTS idx_work_photos_client_photo ON public.work_photos(work_id, client_photo_id);

-- 7. Create Approvals Table
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

CREATE INDEX IF NOT EXISTS idx_approvals_work ON public.approvals(work_id);

-- Ensure uq_approval_work_role unique constraint exists even if table was created previously without it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_approval_work_role'
    ) THEN
        ALTER TABLE public.approvals ADD CONSTRAINT uq_approval_work_role UNIQUE (work_id, approver_role);
    END IF;
END $$;

-- 8. Create Activity Events Table
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

CREATE INDEX IF NOT EXISTS idx_activity_work ON public.activity_events(work_id);
CREATE INDEX IF NOT EXISTS idx_activity_timestamp ON public.activity_events(event_timestamp);

-- 9. Create Notifications Table
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

CREATE INDEX IF NOT EXISTS idx_notifications_user ON public.notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_work ON public.notifications(work_id);

-- 10. Create Work Reports Table
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

CREATE INDEX IF NOT EXISTS idx_work_reports_work_id ON public.work_reports(work_id);

-- 11. Create Storage Buckets
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'storage' AND table_name = 'buckets') THEN
        INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
        VALUES 
            ('work-photos', 'work-photos', false, 20971520, ARRAY['image/jpeg', 'image/png', 'image/webp']),
            ('work-reports', 'work-reports', false, 52428800, ARRAY['application/pdf'])
        ON CONFLICT (id) DO UPDATE SET
            public = false;
    END IF;
END $$;

-- ====================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
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

DROP POLICY IF EXISTS works_service_boy_update ON public.works;
CREATE POLICY works_service_boy_update ON public.works
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

-- Storage RLS
DO $$
BEGIN
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
EXCEPTION WHEN OTHERS THEN
    NULL; -- Storage policies will apply if storage.objects exists
END $$;

-- ====================================================================
-- SERVER-AUTHORITATIVE ATOMIC RPC FUNCTIONS
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
        -- Approve
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

    -- Verify Approvals
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

    -- Activity Event
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

-- 8. Get My Works (Filtered by Caller Role)
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
-- SEED DATA & AUTH USER PROVISIONING
-- ====================================================================

-- Provision Demo Auth Accounts in auth.users and auth.identities
-- Uses DO block without any invalid ON CONFLICT (email)
DO $$
DECLARE
    v_service_id UUID;
    v_poc_id UUID;
    v_supervisor_id UUID;
BEGIN
    -- 1. service@demo.com / demo1234
    SELECT id INTO v_service_id FROM auth.users WHERE email = 'service@demo.com' LIMIT 1;
    IF v_service_id IS NULL THEN
        v_service_id := '11111111-1111-1111-1111-111111111111'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_service_id,
            'authenticated',
            'authenticated',
            'service@demo.com',
            crypt('demo1234', gen_salt('bf')),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Rahul Patil","role":"SERVICE_BOY"}'::jsonb,
            NOW(),
            NOW()
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf')),
            email_confirmed_at = NOW(),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Rahul Patil","role":"SERVICE_BOY"}'::jsonb,
            updated_at = NOW()
        WHERE id = v_service_id;
    END IF;

    -- Link identity for service@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_service_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            v_service_id, v_service_id,
            format('{"sub":"%s","email":"service@demo.com"}', v_service_id)::jsonb,
            'email', v_service_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = format('{"sub":"%s","email":"service@demo.com"}', v_service_id)::jsonb,
            updated_at = NOW()
        WHERE user_id = v_service_id AND provider = 'email';
    END IF;

    -- 2. poc@demo.com / demo1234
    SELECT id INTO v_poc_id FROM auth.users WHERE email = 'poc@demo.com' LIMIT 1;
    IF v_poc_id IS NULL THEN
        v_poc_id := '22222222-2222-2222-2222-222222222222'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_poc_id,
            'authenticated',
            'authenticated',
            'poc@demo.com',
            crypt('demo1234', gen_salt('bf')),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Amit Sharma","role":"POC"}'::jsonb,
            NOW(),
            NOW()
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf')),
            email_confirmed_at = NOW(),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Amit Sharma","role":"POC"}'::jsonb,
            updated_at = NOW()
        WHERE id = v_poc_id;
    END IF;

    -- Link identity for poc@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_poc_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            v_poc_id, v_poc_id,
            format('{"sub":"%s","email":"poc@demo.com"}', v_poc_id)::jsonb,
            'email', v_poc_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = format('{"sub":"%s","email":"poc@demo.com"}', v_poc_id)::jsonb,
            updated_at = NOW()
        WHERE user_id = v_poc_id AND provider = 'email';
    END IF;

    -- 3. supervisor@demo.com / demo1234
    SELECT id INTO v_supervisor_id FROM auth.users WHERE email = 'supervisor@demo.com' LIMIT 1;
    IF v_supervisor_id IS NULL THEN
        v_supervisor_id := '33333333-3333-3333-3333-333333333333'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_supervisor_id,
            'authenticated',
            'authenticated',
            'supervisor@demo.com',
            crypt('demo1234', gen_salt('bf')),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Suresh Patil","role":"SITE_SUPERVISOR"}'::jsonb,
            NOW(),
            NOW()
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf')),
            email_confirmed_at = NOW(),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Suresh Patil","role":"SITE_SUPERVISOR"}'::jsonb,
            updated_at = NOW()
        WHERE id = v_supervisor_id;
    END IF;

    -- Link identity for supervisor@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_supervisor_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            v_supervisor_id, v_supervisor_id,
            format('{"sub":"%s","email":"supervisor@demo.com"}', v_supervisor_id)::jsonb,
            'email', v_supervisor_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = format('{"sub":"%s","email":"supervisor@demo.com"}', v_supervisor_id)::jsonb,
            updated_at = NOW()
        WHERE user_id = v_supervisor_id AND provider = 'email';
    END IF;
END $$;

-- Clean up any rogue rows in public.users with demo emails under non-standard IDs
DELETE FROM public.users WHERE email IN ('service@demo.com', 'poc@demo.com', 'supervisor@demo.com') AND id NOT IN (1, 2, 3);

-- Provision public.users (explicit match on primary key id)
INSERT INTO public.users (id, auth_user_id, name, email, phone, role, is_active)
VALUES 
    (1, (SELECT id FROM auth.users WHERE email = 'service@demo.com' LIMIT 1), 'Rahul Patil', 'service@demo.com', '+91 98765 43210', 'SERVICE_BOY', true),
    (2, (SELECT id FROM auth.users WHERE email = 'poc@demo.com' LIMIT 1), 'Amit Sharma', 'poc@demo.com', '+91 98765 43211', 'POC', true),
    (3, (SELECT id FROM auth.users WHERE email = 'supervisor@demo.com' LIMIT 1), 'Suresh Patil', 'supervisor@demo.com', '+91 98765 43212', 'SITE_SUPERVISOR', true)
ON CONFLICT (id) DO UPDATE SET
    auth_user_id = EXCLUDED.auth_user_id,
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active;

SELECT setval('public.users_id_seq', (SELECT MAX(id) FROM public.users));

-- Clean up any prior seed records for Works 1 and 2 to guarantee 100% idempotent clean recreation
DELETE FROM public.notifications WHERE work_id IN (1, 2);
DELETE FROM public.work_reports WHERE work_id IN (1, 2) OR id = 1 OR report_number = 'REP-W1-1789270200446';
DELETE FROM public.activity_events WHERE work_id IN (1, 2) OR id IN (1, 2, 3, 4, 5, 6);
DELETE FROM public.work_photos WHERE work_id IN (1, 2);
DELETE FROM public.additional_works WHERE work_id IN (1, 2);
DELETE FROM public.approvals WHERE work_id IN (1, 2) OR id IN (1, 2);
DELETE FROM public.work_checklist_items WHERE work_id IN (1, 2) OR id IN (1, 2, 3, 4, 5, 6);
DELETE FROM public.works WHERE id IN (1, 2);

-- 1. Historical Work 1 (COMPLETED)
INSERT INTO public.works (
    id, title, work_type, description, notes, scheduled_date, status,
    service_boy_id, poc_id, supervisor_id, company_name, address,
    latitude, longitude, allowed_radius_meters, start_time, submitted_at, completed_at
) VALUES (
    1,
    'Monthly Pest Control Service',
    'PEST_CONTROL',
    'Comprehensive monthly pest control treatment for entire office premises including pantry, cafeteria, washrooms, and workstations.',
    'Pantry and washrooms are high priority',
    CURRENT_DATE,
    'COMPLETED',
    1, 2, 3,
    'Acme Corp - Main Office',
    'Plot 42, MIDC Industrial Area, Ratnagiri, Maharashtra 415612',
    17.5230403, 73.5378423, 150.0,
    NOW() - INTERVAL '2 hours',
    NOW() - INTERVAL '1 hour',
    NOW() - INTERVAL '30 minutes'
);

-- Historical Work 1 Checklist
INSERT INTO public.work_checklist_items (id, work_id, title, description, is_completed, display_order)
VALUES 
    (1, 1, 'General Site Inspection', 'Inspect premises for pest activity', true, 1),
    (2, 1, 'Pest Control Treatment', 'Apply treatment in cafeteria and pantry', true, 2),
    (3, 1, 'Equipment Inspection', 'Check traps and bait stations', true, 3),
    (4, 1, 'Preventive Maintenance Check', 'Verify pest barrier seals', true, 4),
    (5, 1, 'Safety Inspection', 'Ensure all chemical safety protocols observed', true, 5),
    (6, 1, 'Area Cleaning', 'Clean and sanitize treated areas', true, 6)
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    is_completed = EXCLUDED.is_completed;

-- Historical Work 1 Approvals (unique constraint: uq_approval_work_role)
INSERT INTO public.approvals (id, work_id, approver_id, approver_role, status, decided_at)
VALUES 
    (1, 1, 2, 'POC', 'APPROVED', NOW() - INTERVAL '45 minutes'),
    (2, 1, 3, 'SITE_SUPERVISOR', 'APPROVED', NOW() - INTERVAL '35 minutes')
ON CONFLICT (work_id, approver_role) DO UPDATE SET
    status = EXCLUDED.status,
    decided_at = EXCLUDED.decided_at;

-- Historical Work 1 Activity Events
INSERT INTO public.activity_events (id, work_id, event_type, description, performed_by_id, event_timestamp, latitude, longitude)
VALUES 
    (1, 1, 'WORK_CREATED', 'Work assigned to Rahul Patil', 3, NOW() - INTERVAL '3 hours', NULL, NULL),
    (2, 1, 'WORK_STARTED', 'Work started at verified location (17.5230, 73.5378, distance: 2m) by Rahul Patil', 1, NOW() - INTERVAL '2 hours', 17.5230231, 73.5378484),
    (3, 1, 'WORK_SUBMITTED', 'Work submitted for review by Rahul Patil', 1, NOW() - INTERVAL '1 hour', NULL, NULL),
    (4, 1, 'POC_APPROVED', 'Work approved by POC: Amit Sharma', 2, NOW() - INTERVAL '45 minutes', NULL, NULL),
    (5, 1, 'SUPERVISOR_APPROVED', 'Work approved by Supervisor: Suresh Patil', 3, NOW() - INTERVAL '35 minutes', NULL, NULL),
    (6, 1, 'WORK_COMPLETED', 'Work completed by Rahul Patil', 1, NOW() - INTERVAL '30 minutes', NULL, NULL)
ON CONFLICT (id) DO UPDATE SET
    description = EXCLUDED.description,
    event_type = EXCLUDED.event_type;

-- Historical Work 1 Report (unique constraint: report_number)
INSERT INTO public.work_reports (id, work_id, report_number, storage_reference, file_name, content_type, file_size, generated_at, created_by, version)
VALUES (
    1,
    1,
    'REP-W1-1789270200446',
    'work-reports/WorkReport_1.pdf',
    'WorkReport_1.pdf',
    'application/pdf',
    1024,
    NOW() - INTERVAL '30 minutes',
    1,
    1
) ON CONFLICT (report_number) DO UPDATE SET
    storage_reference = EXCLUDED.storage_reference,
    file_name = EXCLUDED.file_name;

-- 2. PHASE 27 FRESH TEST WORK: 'Monthly Pest Control Service - SUPABASE TEST'
INSERT INTO public.works (
    id, title, work_type, description, notes, scheduled_date, status,
    service_boy_id, poc_id, supervisor_id, company_name, address,
    latitude, longitude, allowed_radius_meters
) VALUES (
    2,
    'Monthly Pest Control Service - SUPABASE TEST',
    'PEST_CONTROL',
    'Fresh end-to-end online Supabase verification service: inspection, barrier treatment, photo proof, dual approval, and digital completion report.',
    'Authoritative GPS Geofence: Ratnagiri premises within 150 meters',
    CURRENT_DATE,
    'ASSIGNED',
    1, 2, 3,
    'TechPark Tower A - Ground Floor',
    'Plot 42, Sector 5, Ratnagiri District, Maharashtra 415612',
    17.5230403, 73.5378423, 150.0
) ON CONFLICT (id) DO UPDATE SET
    status = 'ASSIGNED',
    start_time = NULL,
    submitted_at = NULL,
    completed_at = NULL,
    latitude = 17.5230403,
    longitude = 73.5378423,
    allowed_radius_meters = 150.0;

SELECT setval('public.works_id_seq', (SELECT MAX(id) FROM public.works));

-- Fresh Test Work Checklist Items (clean recreate for Work 2)
DELETE FROM public.work_checklist_items WHERE work_id = 2;
INSERT INTO public.work_checklist_items (work_id, title, description, is_completed, display_order)
VALUES 
    (2, 'General Site Inspection', 'Inspect perimeter and office zones for pest activity', false, 1),
    (2, 'Pest Control Treatment', 'Apply eco-safe pest barrier spray along walls and openings', false, 2),
    (2, 'Equipment Inspection', 'Check and calibrate misting and bait dispensers', false, 3),
    (2, 'Preventive Maintenance Check', 'Verify door sweeps, drain covers, and window mesh seals', false, 4),
    (2, 'Safety Inspection', 'Ensure all chemical safety protocols and PPE worn', false, 5),
    (2, 'Area Cleaning', 'Sanitize and clean application areas after treatment', false, 6);

-- Fresh Test Work Approvals (clean recreate for Work 2)
DELETE FROM public.approvals WHERE work_id = 2;
INSERT INTO public.approvals (work_id, approver_id, approver_role, status)
VALUES 
    (2, 2, 'POC', 'PENDING'),
    (2, 3, 'SITE_SUPERVISOR', 'PENDING');

-- Fresh Test Work Activity Events (clean recreate for Work 2)
DELETE FROM public.activity_events WHERE work_id = 2;
INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
VALUES (2, 'WORK_CREATED', 'Work assigned to Rahul Patil (Ready for GPS Start)', 3, NOW());

-- Reset sequence counters for all tables
SELECT setval('public.users_id_seq', COALESCE((SELECT MAX(id) FROM public.users), 1));
SELECT setval('public.works_id_seq', COALESCE((SELECT MAX(id) FROM public.works), 1));
SELECT setval('public.work_checklist_items_id_seq', COALESCE((SELECT MAX(id) FROM public.work_checklist_items), 1));
SELECT setval('public.additional_works_id_seq', COALESCE((SELECT MAX(id) FROM public.additional_works), 1));
SELECT setval('public.work_photos_id_seq', COALESCE((SELECT MAX(id) FROM public.work_photos), 1));
SELECT setval('public.approvals_id_seq', COALESCE((SELECT MAX(id) FROM public.approvals), 1));
SELECT setval('public.activity_events_id_seq', COALESCE((SELECT MAX(id) FROM public.activity_events), 1));
SELECT setval('public.notifications_id_seq', COALESCE((SELECT MAX(id) FROM public.notifications), 1));
SELECT setval('public.work_reports_id_seq', COALESCE((SELECT MAX(id) FROM public.work_reports), 1));

-- ====================================================================
-- END OF SUPABASE SCHEMA MIGRATION SCRIPT
-- ====================================================================
