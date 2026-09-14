-- ====================================================================
-- FIELD SERVICE MANAGEMENT APPLICATION
-- INCREMENTAL SUPABASE MIGRATION: WORKFLOW HARDENING & ADMIN MONITORING
-- Destination: supabase/migrations/20260914_workflow_hardening.sql
-- Project: https://ajgkqjemiqsyqirainok.supabase.co
--
-- This migration safely hardens the production Supabase backend:
-- 1. Fixes PostgreSQL FORMAT() error in start_work: replaces invalid %.4f with %s and ROUND()
-- 2. Makes start_work, poc_decision, and supervisor_decision strictly idempotent
-- 3. Adds secure add_additional_work RPC deriving created_by_id from auth.uid()
-- 4. Extends public.users role constraint to support 'ADMIN'
-- 5. Updates get_my_works() to support read-only operational monitoring for ADMIN
-- 6. Grants ADMIN read-only RLS visibility across all application tables and private storage
-- 7. Seeds admin@demo.com demo account
-- ====================================================================

-- ====================================================================
-- 1. UPDATE ROLE CONSTRAINT ON public.users TO INCLUDE 'ADMIN'
-- ====================================================================
DO $$
BEGIN
    ALTER TABLE public.users DROP CONSTRAINT IF EXISTS users_role_check;
    ALTER TABLE public.users ADD CONSTRAINT users_role_check 
        CHECK (role IN ('SERVICE_BOY', 'POC', 'SITE_SUPERVISOR', 'ADMIN'));
END $$;

-- ====================================================================
-- 2. HARDEN start_work RPC (Fix FORMAT() bug + Idempotency)
-- ====================================================================
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

    -- Idempotency check: If already in progress by this service boy, return immediately
    IF v_work.status = 'IN_PROGRESS' THEN
        RETURN to_jsonb(v_work);
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
        RAISE EXCEPTION 'Outside permitted work location. Distance: %m, Allowed radius: %m', ROUND(v_distance::numeric, 1), ROUND(v_allowed_radius::numeric, 1);
    END IF;

    -- Transition state
    UPDATE public.works
    SET status = 'IN_PROGRESS',
        start_time = v_now,
        updated_at = v_now
    WHERE id = p_work_id
    RETURNING * INTO v_work;

    -- Record activity event with standard %s format specifiers
    INSERT INTO public.activity_events (
        work_id, event_type, description, performed_by_id, event_timestamp, latitude, longitude, accuracy_meters
    ) VALUES (
        p_work_id,
        'WORK_STARTED',
        FORMAT('Work started at verified location (%s, %s, distance: %sm) by %s', 
            ROUND(p_latitude::numeric, 4), 
            ROUND(p_longitude::numeric, 4), 
            ROUND(v_distance::numeric, 1), 
            v_caller_name),
        v_caller_id,
        v_now,
        p_latitude,
        p_longitude,
        p_accuracy_meters
    );

    RETURN to_jsonb(v_work);
END;
$$;

-- ====================================================================
-- 3. HARDEN poc_decision RPC (Idempotency against repeat clicks/retries)
-- ====================================================================
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

    -- Idempotency check: If already approved, return the existing approval gracefully
    IF v_work.status IN ('POC_APPROVED', 'SUPERVISOR_APPROVED', 'COMPLETED') AND UPPER(p_decision) = 'APPROVED' THEN
        SELECT * INTO v_approval FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'POC';
        RETURN to_jsonb(v_approval);
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

-- ====================================================================
-- 4. HARDEN supervisor_decision RPC (Enforce POC Approval + Idempotency)
-- ====================================================================
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
    v_approval public.approvals%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
BEGIN
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'SITE_SUPERVISOR' THEN
        RAISE EXCEPTION 'Access Denied: Only SITE_SUPERVISOR can make supervisor decision';
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = p_work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work not found: %', p_work_id;
    END IF;

    IF v_work.supervisor_id != v_caller_id THEN
        RAISE EXCEPTION 'Access Denied: You are not the assigned Supervisor for this work';
    END IF;

    -- Idempotency check: If already approved, return existing approval
    IF v_work.status IN ('SUPERVISOR_APPROVED', 'COMPLETED') AND UPPER(p_decision) = 'APPROVED' THEN
        SELECT * INTO v_approval FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'SITE_SUPERVISOR';
        RETURN to_jsonb(v_approval);
    END IF;

    -- Strict business rule: POC must have approved first!
    IF v_work.status != 'POC_APPROVED' THEN
        RAISE EXCEPTION 'Invalid workflow state: Work must be POC_APPROVED before Supervisor review. Current status: %', v_work.status;
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

        -- Notify Service Boy
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'SUPERVISOR_APPROVED', 'Work Approved - Ready to Complete',
            FORMAT('%s approved work for %s. You may now generate final report and complete work.', v_caller_name, v_work.title)
        );

        -- Notify POC
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.poc_id, p_work_id, 'SUPERVISOR_APPROVED', 'Supervisor Approved Work',
            FORMAT('Supervisor %s approved work for %s.', v_caller_name, v_work.title)
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

-- ====================================================================
-- 5. ATOMIC add_additional_work RPC (Derives created_by_id from auth.uid())
-- ====================================================================
CREATE OR REPLACE FUNCTION public.add_additional_work(
    p_work_id BIGINT,
    p_description TEXT,
    p_client_item_id VARCHAR DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_item public.additional_works%ROWTYPE;
BEGIN
    SELECT id, role INTO v_caller_id, v_caller_role
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Unauthorized: User profile not found';
    END IF;

    IF v_caller_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Access Denied: Only SERVICE_BOY can record additional work';
    END IF;

    IF p_description IS NULL OR TRIM(p_description) = '' THEN
        RAISE EXCEPTION 'Additional work description cannot be empty';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.works WHERE id = p_work_id AND service_boy_id = v_caller_id) THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    INSERT INTO public.additional_works (work_id, description, client_item_id, created_by_id)
    VALUES (p_work_id, TRIM(p_description), p_client_item_id, v_caller_id)
    RETURNING * INTO v_item;

    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (p_work_id, 'ADDITIONAL_WORK_ADDED', FORMAT('Additional work recorded: %s', TRIM(p_description)), v_caller_id, NOW());

    RETURN to_jsonb(v_item);
END;
$$;

-- ====================================================================
-- 6. UPDATE get_my_works() FOR SERVICE BOY & ADMIN READ-ONLY MONITORING
-- ====================================================================
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
    ELSIF v_caller_role = 'ADMIN' THEN
        -- Admin has read-only monitoring access to all works
        RETURN QUERY SELECT * FROM public.works ORDER BY id ASC;
    END IF;
END;
$$;

-- ====================================================================
-- 7. UPDATE RLS POLICIES FOR ADMIN READ-ONLY VISIBILITY
-- ====================================================================

-- works
DROP POLICY IF EXISTS works_stakeholders_read ON public.works;
CREATE POLICY works_stakeholders_read ON public.works
    FOR SELECT TO authenticated
    USING (
        service_boy_id = public.get_auth_user_id()
        OR poc_id = public.get_auth_user_id()
        OR supervisor_id = public.get_auth_user_id()
        OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN')
    );

-- checklist
DROP POLICY IF EXISTS checklist_read ON public.work_checklist_items;
CREATE POLICY checklist_read ON public.work_checklist_items
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- additional works
DROP POLICY IF EXISTS additional_works_read ON public.additional_works;
CREATE POLICY additional_works_read ON public.additional_works
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- photos
DROP POLICY IF EXISTS photos_read ON public.work_photos;
CREATE POLICY photos_read ON public.work_photos
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- approvals
DROP POLICY IF EXISTS approvals_read ON public.approvals;
CREATE POLICY approvals_read ON public.approvals
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- activity events
DROP POLICY IF EXISTS activity_read ON public.activity_events;
CREATE POLICY activity_read ON public.activity_events
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- work reports
DROP POLICY IF EXISTS reports_read ON public.work_reports;
CREATE POLICY reports_read ON public.work_reports
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.works w
            WHERE w.id = work_id
            AND (w.service_boy_id = public.get_auth_user_id()
                 OR w.poc_id = public.get_auth_user_id()
                 OR w.supervisor_id = public.get_auth_user_id()
                 OR EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'))
        )
    );

-- ====================================================================
-- 8. PROVISION DEMO ADMIN ACCOUNT: admin@demo.com (demo1234)
-- ====================================================================
DO $$
DECLARE
    v_admin_id UUID;
BEGIN
    SELECT id INTO v_admin_id FROM auth.users WHERE email = 'admin@demo.com' LIMIT 1;
    IF v_admin_id IS NULL THEN
        v_admin_id := '44444444-4444-4444-4444-444444444444'::UUID;
        INSERT INTO auth.users (
            instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
            raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
            confirmation_token, recovery_token, email_change_token_new, email_change,
            email_change_token_current, phone_change, phone_change_token, reauthentication_token
        ) VALUES (
            '00000000-0000-0000-0000-000000000000',
            v_admin_id,
            'authenticated',
            'authenticated',
            'admin@demo.com',
            crypt('demo1234', gen_salt('bf', 10)),
            NOW(),
            '{"provider":"email","providers":["email"]}'::jsonb,
            '{"name":"Operations Admin","role":"ADMIN"}'::jsonb,
            NOW(),
            NOW(),
            '', '', '', '', '', '', '', ''
        );
    ELSE
        UPDATE auth.users
        SET encrypted_password = crypt('demo1234', gen_salt('bf', 10)),
            email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
            raw_user_meta_data = '{"name":"Operations Admin","role":"ADMIN"}'::jsonb,
            confirmation_token = COALESCE(confirmation_token, ''),
            recovery_token = COALESCE(recovery_token, ''),
            email_change_token_new = COALESCE(email_change_token_new, ''),
            email_change = COALESCE(email_change, ''),
            email_change_token_current = COALESCE(email_change_token_current, ''),
            phone_change = COALESCE(phone_change, ''),
            phone_change_token = COALESCE(phone_change_token, ''),
            reauthentication_token = COALESCE(reauthentication_token, ''),
            updated_at = NOW()
        WHERE id = v_admin_id;
    END IF;

    -- Identity for admin@demo.com
    IF NOT EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_admin_id AND provider = 'email') THEN
        INSERT INTO auth.identities (
            id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), v_admin_id,
            jsonb_build_object('sub', v_admin_id::text, 'email', 'admin@demo.com', 'email_verified', true, 'phone_verified', false),
            'email', v_admin_id::text, NOW(), NOW(), NOW()
        );
    ELSE
        UPDATE auth.identities
        SET identity_data = jsonb_build_object('sub', v_admin_id::text, 'email', 'admin@demo.com', 'email_verified', true, 'phone_verified', false),
            provider_id = v_admin_id::text,
            updated_at = NOW()
        WHERE user_id = v_admin_id AND provider = 'email';
    END IF;

    -- public.users entry for Admin
    IF EXISTS (SELECT 1 FROM public.users WHERE email = 'admin@demo.com') THEN
        UPDATE public.users
        SET auth_user_id = v_admin_id,
            name = 'Operations Admin',
            phone = '+91 98765 43213',
            role = 'ADMIN',
            is_active = true,
            updated_at = NOW()
        WHERE email = 'admin@demo.com';
    ELSE
        INSERT INTO public.users (auth_user_id, name, email, phone, role, is_active)
        VALUES (v_admin_id, 'Operations Admin', 'admin@demo.com', '+91 98765 43213', 'ADMIN', true);
    END IF;
END $$;
