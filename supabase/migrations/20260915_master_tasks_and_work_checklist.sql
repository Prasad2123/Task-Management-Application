-- Migration: 20260915_master_tasks_and_work_checklist.sql
-- Description: Master task catalog, authoritative work checklist items, unassigned additional work support, and admin work creation.

-- ====================================================================
-- 1. MASTER TASKS CATALOG TABLE
-- ====================================================================
CREATE TABLE IF NOT EXISTS public.master_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_label VARCHAR(200) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed predefined/common master tasks
INSERT INTO public.master_tasks (task_label, category, display_order) VALUES
    ('General Site Inspection', 'INSPECTION', 1),
    ('Pest Control Treatment', 'TREATMENT', 2),
    ('Equipment Inspection', 'INSPECTION', 3),
    ('Preventive Maintenance Check', 'MAINTENANCE', 4),
    ('Safety Inspection', 'SAFETY', 5),
    ('Area Cleaning', 'CLEANING', 6),
    ('Electrical Inspection', 'ELECTRICAL', 7),
    ('HVAC Inspection', 'HVAC', 8),
    ('Equipment Cleaning', 'CLEANING', 9),
    ('Deep Cleaning', 'CLEANING', 10),
    ('Additional Pest Treatment', 'TREATMENT', 11),
    ('Equipment Repair', 'REPAIR', 12),
    ('Additional Area Inspection', 'INSPECTION', 13)
ON CONFLICT (task_label) DO NOTHING;

-- Enable RLS for master_tasks
ALTER TABLE public.master_tasks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS master_tasks_read ON public.master_tasks;
CREATE POLICY master_tasks_read ON public.master_tasks
    FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS master_tasks_admin_all ON public.master_tasks;
CREATE POLICY master_tasks_admin_all ON public.master_tasks
    FOR ALL TO authenticated
    USING (EXISTS (SELECT 1 FROM public.users WHERE auth_user_id = auth.uid() AND role = 'ADMIN'));

-- ====================================================================
-- 2. ENHANCE work_checklist_items WITH master_task_id & task_label
-- ====================================================================
ALTER TABLE public.work_checklist_items
    ADD COLUMN IF NOT EXISTS master_task_id BIGINT REFERENCES public.master_tasks(id),
    ADD COLUMN IF NOT EXISTS task_label VARCHAR(200);

-- Backfill task_label and master_task_id
UPDATE public.work_checklist_items
SET task_label = title
WHERE task_label IS NULL;

UPDATE public.work_checklist_items wci
SET master_task_id = mt.id
FROM public.master_tasks mt
WHERE mt.task_label = wci.task_label AND wci.master_task_id IS NULL;

-- Trigger: Authoritative Server-Side Checkbox Persistence
CREATE OR REPLACE FUNCTION public.trg_fn_checklist_completion()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_completed = TRUE AND (OLD.is_completed IS DISTINCT FROM TRUE) THEN
        NEW.completed_at = NOW();
        NEW.completed_by_id = COALESCE(public.get_auth_user_id(), NEW.completed_by_id);
    ELSIF NEW.is_completed = FALSE AND (OLD.is_completed IS DISTINCT FROM FALSE) THEN
        NEW.completed_at = NULL;
        NEW.completed_by_id = NULL;
    END IF;
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_checklist_completion ON public.work_checklist_items;
CREATE TRIGGER trg_checklist_completion
    BEFORE UPDATE ON public.work_checklist_items
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_fn_checklist_completion();

-- ====================================================================
-- 3. ENHANCE additional_works WITH master_task_id & task_label
-- ====================================================================
ALTER TABLE public.additional_works
    ADD COLUMN IF NOT EXISTS master_task_id BIGINT REFERENCES public.master_tasks(id),
    ADD COLUMN IF NOT EXISTS task_label VARCHAR(200);

UPDATE public.additional_works
SET task_label = description
WHERE task_label IS NULL;

UPDATE public.additional_works aw
SET master_task_id = mt.id
FROM public.master_tasks mt
WHERE mt.task_label = aw.task_label AND aw.master_task_id IS NULL;

-- ====================================================================
-- 4. UPDATE add_additional_work RPC WITH DEDUPLICATION & AUTHORITATIVE PERSISTENCE
-- ====================================================================
CREATE OR REPLACE FUNCTION public.add_additional_work(
    p_work_id BIGINT,
    p_description TEXT DEFAULT NULL,
    p_client_item_id VARCHAR DEFAULT NULL,
    p_master_task_id BIGINT DEFAULT NULL,
    p_task_label TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_final_label TEXT;
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

    -- A task already assigned to the work should not appear or be added as Additional Work
    IF p_master_task_id IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM public.work_checklist_items
            WHERE work_id = p_work_id AND master_task_id = p_master_task_id
        ) THEN
            RAISE EXCEPTION 'Task already assigned to this work checklist';
        END IF;

        IF p_task_label IS NULL OR TRIM(p_task_label) = '' THEN
            SELECT task_label INTO v_final_label FROM public.master_tasks WHERE id = p_master_task_id;
        ELSE
            v_final_label := TRIM(p_task_label);
        END IF;
    ELSE
        v_final_label := COALESCE(NULLIF(TRIM(p_task_label), ''), NULLIF(TRIM(p_description), ''));
    END IF;

    IF v_final_label IS NULL OR v_final_label = '' THEN
        RAISE EXCEPTION 'Additional work description or task label cannot be empty';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.works WHERE id = p_work_id AND service_boy_id = v_caller_id) THEN
        RAISE EXCEPTION 'Access Denied: You are not assigned to this work';
    END IF;

    INSERT INTO public.additional_works (work_id, description, client_item_id, created_by_id, master_task_id, task_label)
    VALUES (p_work_id, v_final_label, p_client_item_id, v_caller_id, p_master_task_id, v_final_label)
    RETURNING * INTO v_item;

    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (p_work_id, 'ADDITIONAL_WORK_ADDED', FORMAT('Additional work recorded: %s', v_final_label), v_caller_id, NOW());

    RETURN to_jsonb(v_item);
END;
$$;

-- ====================================================================
-- 5. UPDATE submit_for_review RPC WITH MANDATORY CHECKLIST VALIDATION
-- ====================================================================
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
    v_pending_count INT;
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

    IF v_work.status != 'IN_PROGRESS' AND v_work.status != 'WORK_STARTED' THEN
        RAISE EXCEPTION 'Work must be IN_PROGRESS to submit for review. Current: %', v_work.status;
    END IF;

    -- CRITICAL BUSINESS RULE: Submission is NOT allowed while any assigned task remains unchecked
    SELECT COUNT(*) INTO v_pending_count
    FROM public.work_checklist_items
    WHERE work_id = p_work_id AND is_completed = FALSE;

    IF v_pending_count > 0 THEN
        RAISE EXCEPTION 'Complete all assigned tasks before submitting.';
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

ALTER TABLE public.works
    ADD COLUMN IF NOT EXISTS google_maps_link TEXT;

-- ====================================================================
-- 6. ADMIN RPC: CREATE WORK WITH SELECTED CHECKLIST ITEMS
-- ====================================================================
CREATE OR REPLACE FUNCTION public.create_work_with_checklist(
    p_company_name VARCHAR,
    p_address VARCHAR,
    p_latitude DOUBLE PRECISION,
    p_longitude DOUBLE PRECISION,
    p_google_maps_link TEXT,
    p_service_boy_id BIGINT,
    p_poc_id BIGINT DEFAULT NULL,
    p_supervisor_id BIGINT DEFAULT NULL,
    p_master_task_ids BIGINT[] DEFAULT ARRAY[]::BIGINT[],
    p_title VARCHAR DEFAULT NULL,
    p_scheduled_date VARCHAR DEFAULT NULL,
    p_allowed_radius_meters DOUBLE PRECISION DEFAULT 150.0
)
RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER
AS $$
DECLARE
    v_caller_id BIGINT;
    v_caller_role VARCHAR;
    v_caller_name VARCHAR;
    v_sb_role VARCHAR;
    v_work public.works%ROWTYPE;
    v_task_id BIGINT;
    v_master_task public.master_tasks%ROWTYPE;
    v_order INT := 1;
    v_now TIMESTAMPTZ := NOW();
    v_final_title VARCHAR;
BEGIN
    -- 1. Verify authenticated Admin
    SELECT id, role, name INTO v_caller_id, v_caller_role, v_caller_name
    FROM public.users WHERE auth_user_id = auth.uid();

    IF v_caller_role != 'ADMIN' THEN
        RAISE EXCEPTION 'Access Denied: Only ADMIN can create works with checklist';
    END IF;

    -- 2. Verify selected Service Boy exists and role is SERVICE_BOY
    SELECT role INTO v_sb_role FROM public.users WHERE id = p_service_boy_id;
    IF v_sb_role IS NULL THEN
        RAISE EXCEPTION 'Selected Service Boy does not exist';
    END IF;
    IF v_sb_role != 'SERVICE_BOY' THEN
        RAISE EXCEPTION 'Selected user is not a Service Boy';
    END IF;

    -- 3. Verify selected Service Boy is FREE
    IF EXISTS (
        SELECT 1 FROM public.works
        WHERE service_boy_id = p_service_boy_id
          AND status IN ('ASSIGNED', 'WORK_STARTED', 'IN_PROGRESS', 'SUBMITTED_FOR_REVIEW', 'WAITING_FOR_REVIEW', 'WAITING_FOR_POC_REVIEW', 'WAITING_FOR_SUPERVISOR_REVIEW')
    ) THEN
        RAISE EXCEPTION 'Selected Service Boy is currently BUSY with another active job';
    END IF;

    -- 4. Validate company name
    IF p_company_name IS NULL OR TRIM(p_company_name) = '' THEN
        RAISE EXCEPTION 'Company name is required';
    END IF;

    -- 5. Validate location
    IF p_address IS NULL OR TRIM(p_address) = '' THEN
        RAISE EXCEPTION 'Location is required';
    END IF;

    -- 6. Validate coordinates
    IF p_latitude IS NULL OR p_longitude IS NULL OR p_latitude < -90.0 OR p_latitude > 90.0 OR p_longitude < -180.0 OR p_longitude > 180.0 THEN
        RAISE EXCEPTION 'Valid coordinates are required (Latitude between -90 and 90, Longitude between -180 and 180)';
    END IF;

    -- 7. Validate Google Maps link
    IF p_google_maps_link IS NULL OR TRIM(p_google_maps_link) = '' THEN
        RAISE EXCEPTION 'Google Maps link is required';
    END IF;

    -- 8. Validate at least one task selected
    IF p_master_task_ids IS NULL OR array_length(p_master_task_ids, 1) = 0 THEN
        RAISE EXCEPTION 'Select at least one task for this work.';
    END IF;

    v_final_title := COALESCE(NULLIF(TRIM(p_title), ''), p_company_name || ' Service Work');

    -- 9. Create work
    INSERT INTO public.works (
        title, company_name, address, status,
        service_boy_id, poc_id, supervisor_id,
        scheduled_date, latitude, longitude, allowed_radius_meters,
        google_maps_link,
        created_at, updated_at
    ) VALUES (
        v_final_title, TRIM(p_company_name), TRIM(p_address), 'NOT_STARTED',
        p_service_boy_id, p_poc_id, p_supervisor_id,
        COALESCE(p_scheduled_date, CURRENT_DATE::TEXT),
        p_latitude, p_longitude, p_allowed_radius_meters,
        TRIM(p_google_maps_link),
        v_now, v_now
    ) RETURNING * INTO v_work;

    -- 10. Create work_checklist_items for every selected master task (snapshot)
    FOREACH v_task_id IN ARRAY p_master_task_ids
    LOOP
        SELECT * INTO v_master_task FROM public.master_tasks WHERE id = v_task_id;
        IF FOUND THEN
            INSERT INTO public.work_checklist_items (
                work_id, master_task_id, task_label, title,
                description, is_completed, is_additional, display_order,
                created_at, updated_at
            ) VALUES (
                v_work.id, v_master_task.id, v_master_task.task_label, v_master_task.task_label,
                FORMAT('Standard requirement: %s', v_master_task.task_label),
                FALSE, FALSE, v_order,
                v_now, v_now
            );
            v_order := v_order + 1;
        END IF;
    END LOOP;

    -- Initialize approvals for POC and Site Supervisor if assigned
    IF p_poc_id IS NOT NULL THEN
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, created_at, updated_at)
        VALUES (v_work.id, p_poc_id, 'POC', 'PENDING', v_now, v_now);
    END IF;
    IF p_supervisor_id IS NOT NULL THEN
        INSERT INTO public.approvals (work_id, approver_id, approver_role, status, created_at, updated_at)
        VALUES (v_work.id, p_supervisor_id, 'SITE_SUPERVISOR', 'PENDING', v_now, v_now);
    END IF;

    -- 11. Authoritative activity event
    INSERT INTO public.activity_events (work_id, event_type, description, performed_by_id, event_timestamp)
    VALUES (v_work.id, 'WORK_CREATED', FORMAT('Work created by Admin %s with %s assigned tasks', v_caller_name, array_length(p_master_task_ids, 1)), v_caller_id, v_now);

    -- 12. Notify Service Boy
    INSERT INTO public.notifications (user_id, work_id, type, title, message)
    VALUES (
        p_service_boy_id,
        v_work.id,
        'WORK_ASSIGNED',
        'New Work Assigned',
        FORMAT('You have been assigned to %s at %s.', v_final_title, p_company_name)
    );

    RETURN to_jsonb(v_work);
END;
$$;
