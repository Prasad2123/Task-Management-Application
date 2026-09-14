-- ====================================================================
-- FIX SUPABASE AUTH SERVICE ERROR: "Database error querying schema"
-- Destination: supabase/migrations/20260914_repair_auth_users.sql
-- Project: https://ajgkqjemiqsyqirainok.supabase.co
--
-- NOTE:
-- In modern Supabase, "confirmed_at" is a GENERATED column automatically
-- computed from email_confirmed_at. Updating email_confirmed_at automatically
-- populates confirmed_at.
-- ====================================================================

DO $$
DECLARE
    col RECORD;
    v_sql TEXT;
    v_svc_id UUID;
    v_poc_id UUID;
    v_sup_id UUID;
BEGIN
    RAISE NOTICE 'Starting Supabase Auth users repair...';

    -- 1. Convert NULL string tokens to '' for all users in auth.users
    FOR col IN 
        SELECT column_name 
        FROM information_schema.columns 
        WHERE table_schema = 'auth' 
          AND table_name = 'users' 
          AND is_generated = 'NEVER'
          AND column_name IN (
              'confirmation_token',
              'recovery_token',
              'email_change_token_new',
              'email_change',
              'email_change_token_current',
              'phone_change',
              'phone_change_token',
              'reauthentication_token'
          )
    LOOP
        v_sql := FORMAT('UPDATE auth.users SET %I = '''' WHERE %I IS NULL;', col.column_name, col.column_name);
        EXECUTE v_sql;
        RAISE NOTICE 'Cleaned NULLs in auth.users.%', col.column_name;
    END LOOP;

    -- 2. Set default numeric / boolean columns if NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'email_change_confirm_status' AND is_generated = 'NEVER'
    ) THEN
        UPDATE auth.users SET email_change_confirm_status = 0 WHERE email_change_confirm_status IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'is_anonymous' AND is_generated = 'NEVER'
    ) THEN
        UPDATE auth.users SET is_anonymous = false WHERE is_anonymous IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'is_sso_user' AND is_generated = 'NEVER'
    ) THEN
        UPDATE auth.users SET is_sso_user = false WHERE is_sso_user IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'auth' AND table_name = 'users' AND column_name = 'is_super_admin' AND is_generated = 'NEVER'
    ) THEN
        UPDATE auth.users SET is_super_admin = false WHERE is_super_admin IS NULL;
    END IF;

    -- 3. Ensure demo users have valid bcrypt encrypted passwords and confirmation states
    -- (email_confirmed_at automatically calculates the generated confirmed_at column)
    UPDATE auth.users
    SET encrypted_password = crypt('demo1234', gen_salt('bf', 10)),
        email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
        aud = 'authenticated',
        role = 'authenticated',
        raw_app_meta_data = '{"provider":"email","providers":["email"]}'::jsonb,
        updated_at = NOW()
    WHERE email IN ('service@demo.com', 'poc@demo.com', 'supervisor@demo.com');

    -- 4. Ensure identities exist and identity_data has email_verified = true
    SELECT id INTO v_svc_id FROM auth.users WHERE email = 'service@demo.com' LIMIT 1;
    SELECT id INTO v_poc_id FROM auth.users WHERE email = 'poc@demo.com' LIMIT 1;
    SELECT id INTO v_sup_id FROM auth.users WHERE email = 'supervisor@demo.com' LIMIT 1;

    -- Fix / Insert identity for service@demo.com
    IF v_svc_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_svc_id AND provider = 'email') THEN
            UPDATE auth.identities
            SET identity_data = jsonb_build_object(
                    'sub', v_svc_id::text,
                    'email', 'service@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                provider_id = v_svc_id::text,
                updated_at = NOW()
            WHERE user_id = v_svc_id AND provider = 'email';
        ELSE
            INSERT INTO auth.identities (
                id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
            ) VALUES (
                gen_random_uuid(),
                v_svc_id,
                jsonb_build_object(
                    'sub', v_svc_id::text,
                    'email', 'service@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                'email',
                v_svc_id::text,
                NOW(),
                NOW(),
                NOW()
            );
        END IF;
    END IF;

    -- Fix / Insert identity for poc@demo.com
    IF v_poc_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_poc_id AND provider = 'email') THEN
            UPDATE auth.identities
            SET identity_data = jsonb_build_object(
                    'sub', v_poc_id::text,
                    'email', 'poc@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                provider_id = v_poc_id::text,
                updated_at = NOW()
            WHERE user_id = v_poc_id AND provider = 'email';
        ELSE
            INSERT INTO auth.identities (
                id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
            ) VALUES (
                gen_random_uuid(),
                v_poc_id,
                jsonb_build_object(
                    'sub', v_poc_id::text,
                    'email', 'poc@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                'email',
                v_poc_id::text,
                NOW(),
                NOW(),
                NOW()
            );
        END IF;
    END IF;

    -- Fix / Insert identity for supervisor@demo.com
    IF v_sup_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM auth.identities WHERE user_id = v_sup_id AND provider = 'email') THEN
            UPDATE auth.identities
            SET identity_data = jsonb_build_object(
                    'sub', v_sup_id::text,
                    'email', 'supervisor@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                provider_id = v_sup_id::text,
                updated_at = NOW()
            WHERE user_id = v_sup_id AND provider = 'email';
        ELSE
            INSERT INTO auth.identities (
                id, user_id, identity_data, provider, provider_id, last_sign_in_at, created_at, updated_at
            ) VALUES (
                gen_random_uuid(),
                v_sup_id,
                jsonb_build_object(
                    'sub', v_sup_id::text,
                    'email', 'supervisor@demo.com',
                    'email_verified', true,
                    'phone_verified', false
                ),
                'email',
                v_sup_id::text,
                NOW(),
                NOW(),
                NOW()
            );
        END IF;
    END IF;

    -- 5. Ensure public.users mapping matches auth.users.id
    UPDATE public.users u
    SET auth_user_id = a.id,
        updated_at = NOW()
    FROM auth.users a
    WHERE u.email = a.email
      AND a.email IN ('service@demo.com', 'poc@demo.com', 'supervisor@demo.com');

    RAISE NOTICE 'Supabase Auth users repair completed successfully!';
END $$;

-- 6. Verification Query
SELECT 
    a.id AS auth_id,
    a.email,
    a.role AS auth_role,
    a.email_confirmed_at IS NOT NULL AS email_confirmed,
    a.confirmation_token = '' AS token_repaired,
    i.id AS identity_id,
    i.provider,
    i.identity_data->>'email_verified' AS identity_email_verified,
    p.id AS public_user_id,
    p.name AS public_user_name,
    p.role AS public_user_role,
    p.auth_user_id = a.id AS auth_user_id_synced
FROM auth.users a
LEFT JOIN auth.identities i ON a.id = i.user_id AND i.provider = 'email'
LEFT JOIN public.users p ON a.email = p.email
WHERE a.email IN ('service@demo.com', 'poc@demo.com', 'supervisor@demo.com')
ORDER BY a.email;
