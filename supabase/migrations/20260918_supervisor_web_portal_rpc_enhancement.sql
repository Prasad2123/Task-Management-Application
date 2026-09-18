-- ====================================================================
-- HARDENED PRODUCTION CONSOLIDATED MIGRATION (Prompt 1 + Prompt 2)
-- Project: https://ajgkqjemiqsyqirainok.supabase.co
-- Execution Order:
--   1. CREATE EXTENSION pgcrypto
--   2. CREATE app_settings
--   3. CREATE private work-photos bucket
--   4. CREATE supervisor_photo_access_grants
--   5. CREATE supervisor_web_approval_requests
--   6. CREATE is_photo_token_authorized()
--   7. CREATE token-authorized storage.objects RLS policy
--   8. CREATE poc_decision()
--   9. CREATE supervisor_web_decision()
--  10. CREATE verify_supervisor_web_token()
--  11. Apply GRANT/REVOKE statements
--  12. Apply sequence permissions
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. EXTENSION: pgcrypto
-- --------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- --------------------------------------------------------------------
-- 2. SYSTEM CONFIGURATION TABLE (portal_base_url)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_settings (
    key VARCHAR(64) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default base URL (idempotent: updates to production URL)
INSERT INTO public.app_settings (key, value, description)
VALUES (
    'portal_base_url',
    'https://taskmanagementwebsite1.netlify.app',
    'Base URL for Supervisor Web Approval Portal (e.g., https://approvals.yourdomain.com)'
)
ON CONFLICT (key) DO UPDATE SET
    value = 'https://taskmanagementwebsite1.netlify.app',
    updated_at = NOW();

-- Safely migrate any historical localhost approval requests to production Netlify URL
UPDATE public.supervisor_web_approval_requests
SET approval_url = REPLACE(approval_url, 'http://localhost:4173', 'https://taskmanagementwebsite1.netlify.app'),
    updated_at = NOW()
WHERE approval_url LIKE '%localhost%';

ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public can view non-sensitive app settings" ON public.app_settings;
CREATE POLICY "Public can view non-sensitive app settings"
ON public.app_settings FOR SELECT
TO anon, authenticated
USING (true);

-- --------------------------------------------------------------------
-- 3. STORAGE: PRIVATE work-photos BUCKET
-- --------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('work-photos', 'work-photos', false, 20971520, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- --------------------------------------------------------------------
-- 4. TABLE: supervisor_photo_access_grants
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.supervisor_photo_access_grants (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    storage_reference TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_photo_grant UNIQUE (token_hash, storage_reference)
);

CREATE INDEX IF NOT EXISTS idx_photo_grants_lookup
ON public.supervisor_photo_access_grants(storage_reference, token_hash, expires_at);

ALTER TABLE public.supervisor_photo_access_grants ENABLE ROW LEVEL SECURITY;

-- --------------------------------------------------------------------
-- 5. TABLE: supervisor_web_approval_requests
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.supervisor_web_approval_requests (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL REFERENCES public.works(id) ON DELETE CASCADE,
    supervisor_id BIGINT NOT NULL REFERENCES public.users(id),
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'REVOKED')),
    approval_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
    accessed_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    rejection_reason TEXT,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED')),
    whatsapp_message_payload TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_supervisor_web_req_work UNIQUE (work_id)
);

CREATE INDEX IF NOT EXISTS idx_web_req_token_hash ON public.supervisor_web_approval_requests(token_hash);
CREATE INDEX IF NOT EXISTS idx_web_req_supervisor ON public.supervisor_web_approval_requests(supervisor_id);
CREATE INDEX IF NOT EXISTS idx_web_req_status ON public.supervisor_web_approval_requests(status);

ALTER TABLE public.supervisor_web_approval_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view web approval request for their works" ON public.supervisor_web_approval_requests;
DROP POLICY IF EXISTS "Service role full access on supervisor_web_approval_requests" ON public.supervisor_web_approval_requests;

CREATE POLICY "Users can view web approval request for their works"
ON public.supervisor_web_approval_requests
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.users u
        WHERE u.auth_user_id = auth.uid()
        AND (
            u.id = supervisor_id
            OR u.role = 'ADMIN'
            OR EXISTS (
                SELECT 1 FROM public.works w
                WHERE w.id = supervisor_web_approval_requests.work_id
                AND (w.service_boy_id = u.id OR w.poc_id = u.id)
            )
        )
    )
);

CREATE POLICY "Service role full access on supervisor_web_approval_requests"
ON public.supervisor_web_approval_requests
FOR ALL
USING (auth.role() = 'service_role')
WITH CHECK (auth.role() = 'service_role');

-- --------------------------------------------------------------------
-- 6. HELPER FUNCTION: is_photo_token_authorized
-- (Depends on: supervisor_photo_access_grants AND supervisor_web_approval_requests)
-- --------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_photo_token_authorized(p_object_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.supervisor_photo_access_grants g
        JOIN public.supervisor_web_approval_requests req ON req.token_hash = g.token_hash
        WHERE g.storage_reference = p_object_name
        AND g.expires_at > NOW()
        AND req.status IN ('PENDING', 'APPROVED', 'REJECTED')
        AND req.expires_at > NOW()
    );
$$;

-- --------------------------------------------------------------------
-- 7. STORAGE RLS: Token-authorized access on storage.objects
-- (Depends on: is_photo_token_authorized AND storage.buckets)
-- --------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'storage' AND table_name = 'objects') THEN
        DROP POLICY IF EXISTS "Photos read access for web approval" ON storage.objects;
        DROP POLICY IF EXISTS "Token-authorized photo access for web approval" ON storage.objects;

        CREATE POLICY "Token-authorized photo access for web approval"
        ON storage.objects
        FOR SELECT
        TO anon
        USING (
            bucket_id = 'work-photos'
            AND public.is_photo_token_authorized(storage.objects.name)
        );
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- --------------------------------------------------------------------
-- 8. RPC: poc_decision
-- --------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.poc_decision(BIGINT, VARCHAR, TEXT);
DROP FUNCTION IF EXISTS public.poc_decision(BIGINT, VARCHAR, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.poc_decision(
    p_work_id BIGINT,
    p_decision VARCHAR,
    p_reason TEXT DEFAULT NULL,
    p_portal_base_url TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_work public.works%ROWTYPE;
    v_approval public.approvals%ROWTYPE;
    v_sup public.users%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
    v_raw_token TEXT;
    v_token_hash VARCHAR(64);
    v_base_url TEXT;
    v_approval_url TEXT;
    v_whatsapp_msg TEXT;
    v_web_req public.supervisor_web_approval_requests%ROWTYPE;
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

    -- Idempotency check: Return existing approval and web request if already approved
    IF (v_work.status IN ('POC_APPROVED', 'SUPERVISOR_APPROVED', 'COMPLETED')
        OR EXISTS (SELECT 1 FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'POC' AND status = 'APPROVED'))
        AND UPPER(p_decision) = 'APPROVED' THEN
        SELECT * INTO v_approval FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'POC';
        SELECT * INTO v_web_req FROM public.supervisor_web_approval_requests WHERE work_id = p_work_id;
        v_approval_url := v_web_req.approval_url;
        IF v_approval_url LIKE '%localhost%' THEN
            v_approval_url := REPLACE(v_approval_url, 'http://localhost:4173', 'https://taskmanagementwebsite1.netlify.app');
            UPDATE public.supervisor_web_approval_requests
            SET approval_url = v_approval_url, updated_at = v_now
            WHERE id = v_web_req.id;
        END IF;
        RETURN jsonb_build_object(
            'success', true,
            'poc_approved', true,
            'approval', to_jsonb(v_approval),
            'supervisor_approval_url', v_approval_url,
            'web_request_created', false,
            'web_request_status', v_web_req.status,
            'expires_at', v_web_req.expires_at
        );
    END IF;

    IF v_work.status != 'SUBMITTED_FOR_REVIEW' THEN
        RAISE EXCEPTION 'Work must be in SUBMITTED_FOR_REVIEW status. Current: %', v_work.status;
    END IF;

    IF UPPER(p_decision) = 'APPROVED' THEN
        -- 1. Record POC approval
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, updated_at)
        VALUES (p_work_id, v_caller_id, 'POC', 'APPROVED', v_now, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'APPROVED', decided_at = v_now, rejection_reason = NULL, updated_at = v_now
        RETURNING * INTO v_approval;

        -- 2. Transition work status
        UPDATE public.works SET status = 'POC_APPROVED', updated_at = v_now WHERE id = p_work_id;

        -- 3. Retrieve supervisor details
        SELECT * INTO v_sup FROM public.users WHERE id = v_work.supervisor_id;

        -- 4. Check if valid supervisor web approval request already exists (Duplicate Protection)
        SELECT * INTO v_web_req FROM public.supervisor_web_approval_requests
        WHERE work_id = p_work_id AND expires_at > v_now;

        IF FOUND AND v_web_req.approval_url IS NOT NULL AND TRIM(v_web_req.approval_url) != '' THEN
            -- Reuse existing valid request and sanitize approval URL
            IF v_web_req.approval_url LIKE '%localhost%' THEN
                v_approval_url := REPLACE(v_web_req.approval_url, 'http://localhost:4173', 'https://taskmanagementwebsite1.netlify.app');
                UPDATE public.supervisor_web_approval_requests
                SET approval_url = v_approval_url, updated_at = v_now
                WHERE id = v_web_req.id;
            ELSE
                v_approval_url := v_web_req.approval_url;
            END IF;
        ELSE
            -- Generate 256-bit cryptographically secure random token & SHA-256 hash
            v_raw_token := encode(gen_random_bytes(32), 'hex');
            v_token_hash := encode(sha256(v_raw_token::bytea), 'hex');

            -- Resolve portal base URL hierarchically (parameter -> app_settings -> default)
            IF p_portal_base_url IS NOT NULL AND TRIM(p_portal_base_url) != '' AND p_portal_base_url NOT LIKE '%localhost%' THEN
                v_base_url := TRIM(p_portal_base_url);
            ELSE
                SELECT value INTO v_base_url FROM public.app_settings WHERE key = 'portal_base_url';
                IF v_base_url IS NULL OR TRIM(v_base_url) = '' OR v_base_url LIKE '%localhost%' THEN
                    v_base_url := 'https://taskmanagementwebsite1.netlify.app';
                END IF;
            END IF;

            -- Also ensure app_settings is synchronized to production
            UPDATE public.app_settings
            SET value = 'https://taskmanagementwebsite1.netlify.app', updated_at = v_now
            WHERE key = 'portal_base_url' AND (value IS NULL OR value LIKE '%localhost%');

            v_approval_url := RTRIM(v_base_url, '/') || '/approve/' || v_raw_token;

            -- Format enterprise WhatsApp notification payload
            v_whatsapp_msg := FORMAT(
                'Supervisor Approval Required%s%sWork ID: #%s%sCompany: %s%sTechnician: %s%sScheduled: %s%s%sReview Evidence & Sign Off:%s%s%s%sNote: This secure approval link is valid for 7 days.',
                E'\n', E'\n',
                p_work_id, E'\n',
                v_work.company_name, E'\n',
                (SELECT name FROM public.users WHERE id = v_work.service_boy_id), E'\n',
                v_work.scheduled_date, E'\n', E'\n',
                E'\n', v_approval_url, E'\n', E'\n'
            );

            -- Atomically insert or update the single Supervisor Web Approval Request
            INSERT INTO public.supervisor_web_approval_requests (
                work_id, supervisor_id, token_hash, status, approval_url,
                created_at, expires_at, delivery_status, whatsapp_message_payload, updated_at
            )
            VALUES (
                p_work_id, v_work.supervisor_id, v_token_hash, 'PENDING', v_approval_url,
                v_now, v_now + INTERVAL '7 days', 'PENDING', v_whatsapp_msg, v_now
            )
            ON CONFLICT (work_id) DO UPDATE SET
                supervisor_id = v_work.supervisor_id,
                token_hash = v_token_hash,
                status = 'PENDING',
                approval_url = v_approval_url,
                created_at = v_now,
                expires_at = v_now + INTERVAL '7 days',
                accessed_at = NULL,
                decided_at = NULL,
                rejection_reason = NULL,
                delivery_status = 'PENDING',
                whatsapp_message_payload = v_whatsapp_msg,
                updated_at = v_now
            RETURNING * INTO v_web_req;

            -- Audit log event: SUPERVISOR_WEB_APPROVAL_REQUEST_CREATED
            INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
            VALUES (p_work_id, 'SUPERVISOR_WEB_APPROVAL_REQUEST_CREATED',
                    FORMAT('Supervisor Web Approval Request initiated for %s (Approval Method: WEB)', COALESCE(v_sup.name, 'Supervisor')),
                    v_caller_id, v_now);
        END IF;

        -- Audit log event: POC_APPROVED
        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'POC_APPROVED', FORMAT('Work approved by POC: %s', v_caller_name), v_caller_id, v_now);

        -- Notifications
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'POC_APPROVED', 'Work Evidence Approved',
            FORMAT('%s approved the work evidence for %s. Supervisor Web Approval Request initiated.', v_caller_name, v_work.title)
        );

        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.supervisor_id, p_work_id, 'SUPERVISOR_WEB_REQUEST',
            FORMAT('Web Approval Required: %s', v_work.title),
            FORMAT('%s has approved work evidence. Please review and approve via the secure Web Portal link.', v_caller_name)
        );

        RETURN jsonb_build_object(
            'success', true,
            'poc_approved', true,
            'approval', to_jsonb(v_approval),
            'supervisor_approval_url', v_approval_url,
            'web_request_created', true,
            'web_request_status', COALESCE(v_web_req.status, 'PENDING'),
            'expires_at', v_web_req.expires_at
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

        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'POC_REJECTED', 'Changes Requested by POC',
            FORMAT('%s requested revisions on %s: "%s"', v_caller_name, v_work.title, TRIM(p_reason))
        );

        RETURN jsonb_build_object(
            'success', true,
            'poc_approved', false,
            'approval', to_jsonb(v_approval),
            'web_request_created', false
        );

    ELSE
        RAISE EXCEPTION 'Invalid decision: %. Must be APPROVED or REJECTED', p_decision;
    END IF;
END;
$$;

-- --------------------------------------------------------------------
-- 9. RPC: supervisor_web_decision
-- --------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.supervisor_web_decision(TEXT, VARCHAR, TEXT);

CREATE OR REPLACE FUNCTION public.supervisor_web_decision(
    p_raw_token TEXT,
    p_decision VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_token_hash VARCHAR(64);
    v_req public.supervisor_web_approval_requests%ROWTYPE;
    v_work public.works%ROWTYPE;
    v_sup public.users%ROWTYPE;
    v_poc_approval public.approvals%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
    v_clean_reason TEXT := TRIM(COALESCE(p_reason, ''));
BEGIN
    IF p_raw_token IS NULL OR TRIM(p_raw_token) = '' THEN
        RAISE EXCEPTION 'Missing approval token';
    END IF;

    v_token_hash := encode(sha256(p_raw_token::bytea), 'hex');

    SELECT * INTO v_req FROM public.supervisor_web_approval_requests
    WHERE token_hash = v_token_hash FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid approval token';
    END IF;

    IF v_now > v_req.expires_at THEN
        UPDATE public.supervisor_web_approval_requests SET status = 'EXPIRED', updated_at = v_now WHERE id = v_req.id;
        RAISE EXCEPTION 'This approval link has expired';
    END IF;

    IF v_req.status != 'PENDING' THEN
        RAISE EXCEPTION 'Single-use violation: This request has already been %', v_req.status;
    END IF;

    SELECT * INTO v_work FROM public.works WHERE id = v_req.work_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Work order not found';
    END IF;

    SELECT * INTO v_poc_approval FROM public.approvals
    WHERE work_id = v_req.work_id AND approver_role = 'POC' AND status = 'APPROVED';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'POC approval prerequisite not met';
    END IF;

    SELECT * INTO v_sup FROM public.users WHERE id = v_req.supervisor_id;

    IF UPPER(p_decision) = 'APPROVED' THEN
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, updated_at)
        VALUES (v_req.work_id, v_req.supervisor_id, 'SITE_SUPERVISOR', 'APPROVED', v_now, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'APPROVED', decided_at = v_now, rejection_reason = NULL, updated_at = v_now;

        UPDATE public.works SET status = 'SUPERVISOR_APPROVED', updated_at = v_now WHERE id = v_req.work_id;

        UPDATE public.supervisor_web_approval_requests
        SET status = 'APPROVED', decided_at = v_now, updated_at = v_now
        WHERE id = v_req.id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (v_req.work_id, 'SUPERVISOR_APPROVED',
                FORMAT('Work approved by Supervisor %s via Web Portal (Approval Method: WEB)', v_sup.name),
                v_req.supervisor_id, v_now);

        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, v_req.work_id, 'SUPERVISOR_APPROVED', 'Work Approved by Supervisor',
            FORMAT('Supervisor %s approved your work via Web Portal. You may now complete and finalize the work.', v_sup.name)
        );

        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.poc_id, v_req.work_id, 'SUPERVISOR_APPROVED', 'Supervisor Signed Off Work',
            FORMAT('Supervisor %s completed final sign-off via Web Portal for %s.', v_sup.name, v_work.title)
        );

        RETURN jsonb_build_object('success', true, 'decision', 'APPROVED', 'decided_at', v_now);

    ELSIF UPPER(p_decision) = 'REJECTED' THEN
        IF v_clean_reason = '' THEN
            RAISE EXCEPTION 'Rejection requires a mandatory non-empty reason';
        END IF;

        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, rejection_reason, updated_at)
        VALUES (v_req.work_id, v_req.supervisor_id, 'SITE_SUPERVISOR', 'REJECTED', v_now, v_clean_reason, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'REJECTED', decided_at = v_now, rejection_reason = v_clean_reason, updated_at = v_now;

        UPDATE public.works SET status = 'REJECTED', updated_at = v_now WHERE id = v_req.work_id;

        UPDATE public.supervisor_web_approval_requests
        SET status = 'REJECTED', decided_at = v_now, rejection_reason = v_clean_reason, updated_at = v_now
        WHERE id = v_req.id;

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (v_req.work_id, 'SUPERVISOR_REJECTED',
                FORMAT('Work rejected by Supervisor %s via Web Portal: "%s"', v_sup.name, v_clean_reason),
                v_req.supervisor_id, v_now);

        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, v_req.work_id, 'SUPERVISOR_REJECTED', 'Changes Requested by Supervisor',
            FORMAT('Supervisor %s requested changes via Web Portal: "%s"', v_sup.name, v_clean_reason)
        );

        RETURN jsonb_build_object('success', true, 'decision', 'REJECTED', 'decided_at', v_now);

    ELSE
        RAISE EXCEPTION 'Invalid decision: %. Must be APPROVED or REJECTED', p_decision;
    END IF;
END;
$$;

-- --------------------------------------------------------------------
-- 10. RPC: verify_supervisor_web_token
-- --------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.verify_supervisor_web_token(TEXT);

CREATE OR REPLACE FUNCTION public.verify_supervisor_web_token(
    p_raw_token TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_token_hash VARCHAR(64);
    v_req public.supervisor_web_approval_requests%ROWTYPE;
    v_work public.works%ROWTYPE;
    v_sup public.users%ROWTYPE;
    v_boy public.users%ROWTYPE;
    v_poc public.users%ROWTYPE;
    v_poc_approval public.approvals%ROWTYPE;
    v_checklist JSONB := '[]'::jsonb;
    v_additional_works JSONB := '[]'::jsonb;
    v_timeline JSONB := '[]'::jsonb;
    v_photos JSONB := '[]'::jsonb;
    v_duration_mins INT := NULL;
    v_now TIMESTAMPTZ := NOW();
    r RECORD;
BEGIN
    IF p_raw_token IS NULL OR TRIM(p_raw_token) = '' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Missing approval token');
    END IF;

    v_token_hash := encode(sha256(p_raw_token::bytea), 'hex');

    SELECT * INTO v_req FROM public.supervisor_web_approval_requests
    WHERE token_hash = v_token_hash;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Invalid approval token');
    END IF;

    -- Check expiration
    IF v_now > v_req.expires_at THEN
        IF v_req.status = 'PENDING' THEN
            UPDATE public.supervisor_web_approval_requests SET status = 'EXPIRED', updated_at = v_now WHERE id = v_req.id;
        END IF;
        RETURN jsonb_build_object(
            'valid', false,
            'error', 'This approval link has expired (7-day validity exceeded)',
            'status', 'EXPIRED'
        );
    END IF;

    -- Retrieve work
    SELECT * INTO v_work FROM public.works WHERE id = v_req.work_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Work order not found');
    END IF;

    -- Retrieve POC approval record
    SELECT * INTO v_poc_approval FROM public.approvals
    WHERE work_id = v_req.work_id AND approver_role = 'POC' AND status = 'APPROVED';

    -- Check already approved state
    IF v_req.status = 'APPROVED' THEN
        RETURN jsonb_build_object(
            'valid', false,
            'error', 'This work order has already been approved by Site Supervisor',
            'status', 'APPROVED',
            'work_id', v_work.id,
            'title', v_work.title,
            'company_name', v_work.company_name,
            'decided_at', v_req.decided_at
        );
    END IF;

    -- Check already rejected state
    IF v_req.status = 'REJECTED' THEN
        RETURN jsonb_build_object(
            'valid', false,
            'error', 'This work order has already been rejected',
            'status', 'REJECTED',
            'work_id', v_work.id,
            'title', v_work.title,
            'company_name', v_work.company_name,
            'decided_at', v_req.decided_at,
            'rejection_reason', v_req.rejection_reason
        );
    END IF;

    IF v_req.status = 'REVOKED' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'This approval link has been revoked', 'status', 'REVOKED');
    END IF;

    -- Enforce POC approval prerequisite
    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'error', 'POC approval prerequisite is missing or invalid');
    END IF;

    -- Update accessed_at timestamp
    UPDATE public.supervisor_web_approval_requests SET accessed_at = v_now, updated_at = v_now WHERE id = v_req.id;

    -- Stakeholders
    SELECT * INTO v_sup FROM public.users WHERE id = v_req.supervisor_id;
    SELECT * INTO v_boy FROM public.users WHERE id = v_work.service_boy_id;
    SELECT * INTO v_poc FROM public.users WHERE id = v_work.poc_id;

    -- Duration calculation
    IF v_work.start_time IS NOT NULL AND v_work.submitted_for_review_at IS NOT NULL THEN
        BEGIN
            v_duration_mins := GREATEST(0, ROUND(EXTRACT(EPOCH FROM (v_work.submitted_for_review_at::timestamptz - v_work.start_time::timestamptz)) / 60)::INT);
        EXCEPTION WHEN OTHERS THEN
            v_duration_mins := NULL;
        END;
    END IF;

    -- Aggregated Checklist Items
    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', c.id,
            'title', c.title,
            'description', COALESCE(c.description, ''),
            'is_completed', c.is_completed,
            'completed_at', c.completed_at,
            'completed_by_name', c.completed_by_name,
            'task_label', c.task_label,
            'display_order', c.display_order
        ) ORDER BY c.display_order ASC, c.id ASC
    ), '[]'::jsonb) INTO v_checklist
    FROM public.work_checklist_items c
    WHERE c.work_id = v_work.id;

    -- Aggregated Additional Works
    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', a.id,
            'description', a.description,
            'task_label', a.task_label,
            'created_at', a.created_at,
            'created_by_name', COALESCE(a.created_by_name, '')
        ) ORDER BY a.created_at ASC
    ), '[]'::jsonb) INTO v_additional_works
    FROM public.additional_works a
    WHERE a.work_id = v_work.id;

    -- Aggregated Activity Timeline Events
    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', ev.id,
            'event_type', ev.event_type,
            'description', ev.description,
            'event_timestamp', ev.event_timestamp,
            'performer_name', COALESCE(u.name, 'System'),
            'latitude', ev.latitude,
            'longitude', ev.longitude,
            'accuracy_meters', ev.accuracy_meters
        ) ORDER BY ev.event_timestamp ASC
    ), '[]'::jsonb) INTO v_timeline
    FROM public.activity_events ev
    LEFT JOIN public.users u ON ev.performed_by_id = u.id
    WHERE ev.work_id = v_work.id;

    -- ----------------------------------------------------------------
    -- SECURE PHOTO GRANT ISSUANCE: Strictly bound to THIS work_id
    -- ----------------------------------------------------------------
    -- 1. Purge expired grants
    DELETE FROM public.supervisor_photo_access_grants
    WHERE expires_at < v_now;

    -- 2. Upsert short-lived grants for photos belonging strictly to this work order
    FOR r IN
        SELECT storage_reference FROM public.work_photos
        WHERE work_id = v_work.id AND upload_status = 'UPLOADED' AND storage_reference IS NOT NULL
    LOOP
        INSERT INTO public.supervisor_photo_access_grants (work_id, token_hash, storage_reference, expires_at)
        VALUES (
            v_work.id,
            v_token_hash,
            r.storage_reference,
            LEAST(v_now + INTERVAL '2 hours', v_req.expires_at)
        )
        ON CONFLICT (token_hash, storage_reference) DO UPDATE SET
            expires_at = EXCLUDED.expires_at;
    END LOOP;

    -- 3. Return aggregated photo metadata
    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id', p.id,
            'title', p.title,
            'category', p.category,
            'caption', p.caption,
            'storage_reference', p.storage_reference,
            'photo_url', p.photo_url,
            'created_at', p.created_at,
            'file_size', p.file_size
        ) ORDER BY p.created_at ASC
    ), '[]'::jsonb) INTO v_photos
    FROM public.work_photos p
    WHERE p.work_id = v_work.id AND p.upload_status = 'UPLOADED';

    RETURN jsonb_build_object(
        'valid', true,
        'status', v_req.status,
        'expires_at', v_req.expires_at,
        'work', jsonb_build_object(
            'id', v_work.id,
            'title', v_work.title,
            'company_name', v_work.company_name,
            'address', v_work.address,
            'scheduled_date', v_work.scheduled_date,
            'start_time', v_work.start_time,
            'completed_time', v_work.submitted_for_review_at,
            'duration_minutes', v_duration_mins,
            'notes', v_work.notes,
            'latitude', v_work.latitude,
            'longitude', v_work.longitude,
            'allowed_radius_meters', v_work.allowed_radius_meters,
            'location_verified', v_work.location_verified,
            'distance_from_work_meters', v_work.distance_from_work_meters
        ),
        'personnel', jsonb_build_object(
            'service_boy', jsonb_build_object(
                'id', v_boy.id,
                'name', v_boy.name,
                'phone', v_boy.phone,
                'email', v_boy.email
            ),
            'poc', jsonb_build_object(
                'id', v_poc.id,
                'name', v_poc.name,
                'phone', v_poc.phone,
                'email', v_poc.email
            ),
            'supervisor', jsonb_build_object(
                'id', v_sup.id,
                'name', v_sup.name,
                'phone', v_sup.phone,
                'email', v_sup.email
            )
        ),
        'poc_approval', jsonb_build_object(
            'status', v_poc_approval.status,
            'decided_at', v_poc_approval.decided_at,
            'rejection_reason', v_poc_approval.rejection_reason
        ),
        'checklist', v_checklist,
        'additional_works', v_additional_works,
        'timeline', v_timeline,
        'photos', v_photos
    );
END;
$$;

-- --------------------------------------------------------------------
-- 10.1 RPC: get_my_works (Ordered by created_at DESC, id DESC)
-- --------------------------------------------------------------------
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
        RETURN QUERY SELECT * FROM public.works WHERE service_boy_id = v_caller_id ORDER BY created_at DESC, id DESC;
    ELSIF v_caller_role = 'POC' THEN
        RETURN QUERY SELECT * FROM public.works WHERE poc_id = v_caller_id ORDER BY created_at DESC, id DESC;
    ELSIF v_caller_role = 'SITE_SUPERVISOR' THEN
        RETURN QUERY SELECT * FROM public.works WHERE supervisor_id = v_caller_id ORDER BY created_at DESC, id DESC;
    ELSIF v_caller_role = 'ADMIN' THEN
        RETURN QUERY SELECT * FROM public.works ORDER BY created_at DESC, id DESC;
    END IF;
END;
$$;

-- --------------------------------------------------------------------
-- 11. GRANT / REVOKE STATEMENTS
-- --------------------------------------------------------------------
-- app_settings permissions
REVOKE ALL ON public.app_settings FROM anon, authenticated;
GRANT SELECT ON public.app_settings TO anon, authenticated;
GRANT ALL ON public.app_settings TO service_role;

-- supervisor_photo_access_grants permissions (internal only)
REVOKE ALL ON public.supervisor_photo_access_grants FROM anon, authenticated;
GRANT ALL ON public.supervisor_photo_access_grants TO service_role;

-- supervisor_web_approval_requests permissions
REVOKE ALL ON public.supervisor_web_approval_requests FROM anon;
GRANT SELECT ON public.supervisor_web_approval_requests TO authenticated;
GRANT ALL ON public.supervisor_web_approval_requests TO service_role;

-- Function execution privileges
GRANT EXECUTE ON FUNCTION public.is_photo_token_authorized(TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.poc_decision(BIGINT, VARCHAR, TEXT, TEXT) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.poc_decision(BIGINT, VARCHAR, TEXT, TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.supervisor_web_decision(TEXT, VARCHAR, TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.verify_supervisor_web_token(TEXT) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_works() TO authenticated;

-- --------------------------------------------------------------------
-- 12. SEQUENCE PERMISSIONS
-- --------------------------------------------------------------------
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated, service_role;
