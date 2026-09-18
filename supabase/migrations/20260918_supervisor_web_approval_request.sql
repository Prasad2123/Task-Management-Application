-- ====================================================================
-- Migration: 20260918_supervisor_web_approval_request.sql
-- Description: Creates the supervisor_web_approval_requests table,
--              hardens poc_decision to atomically create web approval requests
--              with SHA-256 hashed high-entropy tokens, adds token verification
--              and web decision RPCs for the Supervisor Web Portal.
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Table: public.supervisor_web_approval_requests
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

-- Enable RLS
ALTER TABLE public.supervisor_web_approval_requests ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if any
DROP POLICY IF EXISTS "Users can view web approval request for their works" ON public.supervisor_web_approval_requests;
DROP POLICY IF EXISTS "Service role full access on supervisor_web_approval_requests" ON public.supervisor_web_approval_requests;

CREATE POLICY "Users can view web approval request for their works"
ON public.supervisor_web_approval_requests
FOR SELECT
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

-- ====================================================================
-- 2. HARDEN poc_decision RPC (Single Atomic Operation for POC Approval + Web Request)
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
    v_sup public.users%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
    v_raw_token TEXT;
    v_token_hash VARCHAR(64);
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

    -- Idempotency check: If already approved, return existing approval and web request
    IF v_work.status IN ('POC_APPROVED', 'SUPERVISOR_APPROVED', 'COMPLETED') AND UPPER(p_decision) = 'APPROVED' THEN
        SELECT * INTO v_approval FROM public.approvals WHERE work_id = p_work_id AND approver_role = 'POC';
        SELECT * INTO v_web_req FROM public.supervisor_web_approval_requests WHERE work_id = p_work_id;
        RETURN jsonb_build_object(
            'approval', to_jsonb(v_approval),
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

        -- 4. Generate 256-bit cryptographically secure token & SHA-256 hash
        v_raw_token := encode(gen_random_bytes(32), 'hex');
        v_token_hash := encode(sha256(v_raw_token::bytea), 'hex');
        v_approval_url := 'https://fieldservice-portal.web.app/approve/' || v_raw_token;

        -- 5. Build pre-formatted WhatsApp message payload
        v_whatsapp_msg := FORMAT(
            'Supervisor Approval Required%s%sWork ID: #%s%sCompany: %s%sTechnician: %s%sScheduled: %s%s%sReview Evidence & Sign Off:%s%s%s%sNote: This secure approval link is valid for 7 days.',
            E'\n', E'\n',
            p_work_id, E'\n',
            v_work.company_name, E'\n',
            (SELECT name FROM public.users WHERE id = v_work.service_boy_id), E'\n',
            v_work.scheduled_date, E'\n', E'\n',
            E'\n', v_approval_url, E'\n', E'\n'
        );

        -- 6. Atomically insert or update the single Supervisor Web Approval Request
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

        -- 7. Audit log events
        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'POC_APPROVED', FORMAT('Work approved by POC: %s', v_caller_name), v_caller_id, v_now);

        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (p_work_id, 'SUPERVISOR_WEB_APPROVAL_REQUEST_CREATED',
                FORMAT('Supervisor Web Approval Request initiated for %s (Approval Method: WEB)', COALESCE(v_sup.name, 'Supervisor')),
                v_caller_id, v_now);

        -- 8. Notifications
        -- Notify Service Boy
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.service_boy_id, p_work_id, 'POC_APPROVED', 'Work Evidence Approved',
            FORMAT('%s approved the work evidence for %s. Supervisor Web Approval Request initiated.', v_caller_name, v_work.title)
        );

        -- Notify Supervisor
        INSERT INTO public.notifications (user_id, work_id, type, title, message)
        VALUES (
            v_work.supervisor_id, p_work_id, 'SUPERVISOR_WEB_REQUEST',
            FORMAT('Web Approval Required: %s', v_work.title),
            FORMAT('%s has approved work evidence. Please review and approve via the secure Web Portal link.', v_caller_name)
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

    RETURN jsonb_build_object(
        'approval', to_jsonb(v_approval),
        'web_request_created', (UPPER(p_decision) = 'APPROVED')
    );
END;
$$;

-- ====================================================================
-- 3. Web Portal Verification RPC: verify_supervisor_web_token
-- ====================================================================
CREATE OR REPLACE FUNCTION public.verify_supervisor_web_token(
    p_raw_token TEXT
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_token_hash VARCHAR(64);
    v_req public.supervisor_web_approval_requests%ROWTYPE;
    v_work public.works%ROWTYPE;
    v_sup public.users%ROWTYPE;
    v_boy public.users%ROWTYPE;
    v_poc public.users%ROWTYPE;
    v_poc_approval public.approvals%ROWTYPE;
    v_now TIMESTAMPTZ := NOW();
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
        RETURN jsonb_build_object('valid', false, 'error', 'This approval link has expired (7-day validity exceeded)');
    END IF;

    -- Check status
    IF v_req.status = 'APPROVED' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'This work order has already been approved', 'status', 'APPROVED');
    END IF;

    IF v_req.status = 'REJECTED' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'This work order has already been rejected', 'status', 'REJECTED');
    END IF;

    IF v_req.status = 'REVOKED' THEN
        RETURN jsonb_build_object('valid', false, 'error', 'This approval link has been revoked');
    END IF;

    -- Retrieve work and verify POC approval prerequisite
    SELECT * INTO v_work FROM public.works WHERE id = v_req.work_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'error', 'Work order not found');
    END IF;

    SELECT * INTO v_poc_approval FROM public.approvals
    WHERE work_id = v_req.work_id AND approver_role = 'POC' AND status = 'APPROVED';

    IF NOT FOUND THEN
        RETURN jsonb_build_object('valid', false, 'error', 'POC approval prerequisite is missing or invalid');
    END IF;

    -- Mark accessed_at timestamp
    UPDATE public.supervisor_web_approval_requests SET accessed_at = v_now, updated_at = v_now WHERE id = v_req.id;

    SELECT * INTO v_sup FROM public.users WHERE id = v_req.supervisor_id;
    SELECT * INTO v_boy FROM public.users WHERE id = v_work.service_boy_id;
    SELECT * INTO v_poc FROM public.users WHERE id = v_work.poc_id;

    RETURN jsonb_build_object(
        'valid', true,
        'work_id', v_work.id,
        'title', v_work.title,
        'company_name', v_work.company_name,
        'address', v_work.address,
        'scheduled_date', v_work.scheduled_date,
        'start_time', v_work.start_time,
        'completed_time', v_work.submitted_for_review_at,
        'notes', v_work.notes,
        'latitude', v_work.latitude,
        'longitude', v_work.longitude,
        'service_boy_name', v_boy.name,
        'poc_name', v_poc.name,
        'poc_approval_time', v_poc_approval.decided_at,
        'supervisor_name', v_sup.name,
        'expires_at', v_req.expires_at,
        'status', v_req.status
    );
END;
$$;

-- ====================================================================
-- 4. Web Portal Decision RPC: supervisor_web_decision
-- ====================================================================
CREATE OR REPLACE FUNCTION public.supervisor_web_decision(
    p_raw_token TEXT,
    p_decision VARCHAR,
    p_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
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

    -- Enforce POC approval prerequisite
    SELECT * INTO v_poc_approval FROM public.approvals
    WHERE work_id = v_req.work_id AND approver_role = 'POC' AND status = 'APPROVED';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'POC approval prerequisite not met';
    END IF;

    SELECT * INTO v_sup FROM public.users WHERE id = v_req.supervisor_id;

    IF UPPER(p_decision) = 'APPROVED' THEN
        -- Record Supervisor approval
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, updated_at)
        VALUES (v_req.work_id, v_req.supervisor_id, 'SITE_SUPERVISOR', 'APPROVED', v_now, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'APPROVED', decided_at = v_now, rejection_reason = NULL, updated_at = v_now;

        -- Update work status
        UPDATE public.works SET status = 'SUPERVISOR_APPROVED', updated_at = v_now WHERE id = v_req.work_id;

        -- Mark web request as APPROVED (single-use consumed)
        UPDATE public.supervisor_web_approval_requests
        SET status = 'APPROVED', decided_at = v_now, updated_at = v_now
        WHERE id = v_req.id;

        -- Audit event
        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (v_req.work_id, 'SUPERVISOR_APPROVED',
                FORMAT('Work approved by Supervisor %s via Web Portal (Approval Method: WEB)', v_sup.name),
                v_req.supervisor_id, v_now);

        -- Notifications
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

        -- Record Supervisor rejection
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, decided_at, rejection_reason, updated_at)
        VALUES (v_req.work_id, v_req.supervisor_id, 'SITE_SUPERVISOR', 'REJECTED', v_now, v_clean_reason, v_now)
        ON CONFLICT (work_id, approver_role) DO UPDATE SET
            status = 'REJECTED', decided_at = v_now, rejection_reason = v_clean_reason, updated_at = v_now;

        -- Update work status
        UPDATE public.works SET status = 'REJECTED', updated_at = v_now WHERE id = v_req.work_id;

        -- Mark web request as REJECTED
        UPDATE public.supervisor_web_approval_requests
        SET status = 'REJECTED', decided_at = v_now, rejection_reason = v_clean_reason, updated_at = v_now
        WHERE id = v_req.id;

        -- Audit event
        INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
        VALUES (v_req.work_id, 'SUPERVISOR_REJECTED',
                FORMAT('Work rejected by Supervisor %s via Web Portal: "%s"', v_sup.name, v_clean_reason),
                v_req.supervisor_id, v_now);

        -- Notifications
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
