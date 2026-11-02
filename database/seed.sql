\set ON_ERROR_STOP on

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- helpers (temporary, vanish when the session ends) ----------

CREATE FUNCTION pg_temp.seed_user(p_name text, p_email text) RETURNS uuid
LANGUAGE plpgsql AS $f$
DECLARE v uuid;
BEGIN
  SELECT id INTO v FROM users WHERE lower(email) = lower(p_email);
  IF v IS NULL THEN
    INSERT INTO users (name, email, password_hash)
    VALUES (p_name, lower(p_email), crypt('Password@123', gen_salt('bf', 10)))
    RETURNING id INTO v;
  END IF;
  RETURN v;
END $f$;

CREATE FUNCTION pg_temp.seed_org(p_name text) RETURNS uuid
LANGUAGE plpgsql AS $f$
DECLARE v uuid;
BEGIN
  SELECT id INTO v FROM organizations WHERE name = p_name ORDER BY created_at LIMIT 1;
  IF v IS NULL THEN
    INSERT INTO organizations (name) VALUES (p_name) RETURNING id INTO v;
  END IF;
  RETURN v;
END $f$;

CREATE FUNCTION pg_temp.seed_tenant(p_org uuid, p_name text) RETURNS uuid
LANGUAGE plpgsql AS $f$
DECLARE v uuid;
BEGIN
  SELECT id INTO v FROM tenants WHERE organization_id = p_org AND name = p_name;
  IF v IS NULL THEN
    INSERT INTO tenants (organization_id, name) VALUES (p_org, p_name) RETURNING id INTO v;
  END IF;
  RETURN v;
END $f$;

CREATE FUNCTION pg_temp.seed_member(p_user uuid, p_org uuid, p_tenant uuid, p_role membership_role)
RETURNS void LANGUAGE plpgsql AS $f$
BEGIN
  IF p_tenant IS NULL THEN
    IF NOT EXISTS (SELECT 1 FROM memberships
                   WHERE user_id = p_user AND organization_id = p_org AND tenant_id IS NULL) THEN
      INSERT INTO memberships (user_id, organization_id, tenant_id, role)
      VALUES (p_user, p_org, NULL, p_role);
    END IF;
  ELSE
    IF NOT EXISTS (SELECT 1 FROM memberships WHERE user_id = p_user AND tenant_id = p_tenant) THEN
      INSERT INTO memberships (user_id, organization_id, tenant_id, role)
      VALUES (p_user, p_org, p_tenant, p_role);
    END IF;
  END IF;
END $f$;

CREATE FUNCTION pg_temp.seed_board(p_org uuid, p_tenant uuid, p_name text, p_desc text, p_by uuid)
RETURNS uuid LANGUAGE plpgsql AS $f$
DECLARE v uuid;
BEGIN
  SELECT id INTO v FROM boards WHERE tenant_id = p_tenant AND name = p_name;
  IF v IS NULL THEN
    INSERT INTO boards (organization_id, tenant_id, name, description, created_by)
    VALUES (p_org, p_tenant, p_name, p_desc, p_by)
    RETURNING id INTO v;
    INSERT INTO columns (board_id, tenant_id, name, "position") VALUES
      (v, p_tenant, 'To Do',       '0001000'),
      (v, p_tenant, 'In Progress', '0002000'),
      (v, p_tenant, 'Testing',     '0003000'),
      (v, p_tenant, 'Done',        '0004000');
    INSERT INTO activity_logs (tenant_id, user_id, entity_type, entity_id, action, details)
    VALUES (p_tenant, p_by, 'BOARD', v, 'CREATED', jsonb_build_object('name', p_name));
  END IF;
  RETURN v;
END $f$;

CREATE FUNCTION pg_temp.seed_task(p_board uuid, p_column text, p_title text, p_desc text,
                                  p_prio task_priority, p_due_days int, p_assignee uuid, p_by uuid)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE v_col uuid; v_tenant uuid; v_pos text; v_tid uuid;
BEGIN
  IF EXISTS (SELECT 1 FROM tasks WHERE board_id = p_board AND title = p_title) THEN
    RETURN;
  END IF;

  SELECT c.id, c.tenant_id INTO v_col, v_tenant
  FROM columns c WHERE c.board_id = p_board AND c.name = p_column;

  SELECT lpad((COALESCE(max("position"::bigint), 0) + 1000)::text, 7, '0')
  INTO v_pos FROM tasks WHERE column_id = v_col;

  INSERT INTO tasks (board_id, column_id, tenant_id, title, description, priority,
                     due_date, assigned_to, created_by, "position")
  VALUES (p_board, v_col, v_tenant, p_title, p_desc, p_prio,
          CASE WHEN p_due_days IS NULL THEN NULL ELSE now() + make_interval(days => p_due_days) END,
          p_assignee, p_by, v_pos)
  RETURNING id INTO v_tid;

  INSERT INTO activity_logs (tenant_id, user_id, entity_type, entity_id, action, details)
  VALUES (v_tenant, p_by, 'TASK', v_tid, 'CREATED',
          jsonb_build_object('title', p_title, 'column', p_column));
END $f$;

-- ---------- data ----------

DO $$
DECLARE
  abc uuid; xyz uuid;
  hyd uuid; blr uuid; che uuid; mum uuid; pun uuid;
  u_rahul uuid; u_harika uuid; u_charan uuid; u_bharath uuid;
  u_sneha uuid; u_saikiran uuid; u_meghana uuid; u_sunny uuid;
  b_web uuid; b_mob uuid; b_crm uuid; b_ai uuid; b_sup uuid;
BEGIN
  u_rahul    := pg_temp.seed_user('Rahul',     'rahul@abc.com');
  u_harika   := pg_temp.seed_user('Harika',    'harika@abc.com');
  u_charan   := pg_temp.seed_user('Charan',    'charan@abc.com');
  u_bharath  := pg_temp.seed_user('Bharath',   'bharath@abc.com');
  u_sneha    := pg_temp.seed_user('Sneha',     'sneha@abc.com');
  u_saikiran := pg_temp.seed_user('Sai Kiran', 'saikiran@abc.com');
  u_meghana  := pg_temp.seed_user('Meghana',   'meghana@xyz.com');
  u_sunny    := pg_temp.seed_user('Sunny',     'sunny@xyz.com');

  abc := pg_temp.seed_org('ABC Technologies');
  xyz := pg_temp.seed_org('XYZ Solutions');

  hyd := pg_temp.seed_tenant(abc, 'Hyderabad');
  blr := pg_temp.seed_tenant(abc, 'Bangalore');
  che := pg_temp.seed_tenant(abc, 'Chennai');
  mum := pg_temp.seed_tenant(xyz, 'Mumbai');
  pun := pg_temp.seed_tenant(xyz, 'Pune');

  PERFORM pg_temp.seed_member(u_rahul,    abc, NULL, 'ORG_ADMIN');
  PERFORM pg_temp.seed_member(u_harika,   abc, hyd,  'TENANT_ADMIN');
  PERFORM pg_temp.seed_member(u_charan,   abc, hyd,  'MEMBER');
  PERFORM pg_temp.seed_member(u_bharath,  abc, hyd,  'MEMBER');
  PERFORM pg_temp.seed_member(u_sneha,    abc, blr,  'TENANT_ADMIN');
  PERFORM pg_temp.seed_member(u_saikiran, abc, blr,  'MEMBER');
  PERFORM pg_temp.seed_member(u_meghana,  xyz, NULL, 'ORG_ADMIN');
  PERFORM pg_temp.seed_member(u_sunny,    xyz, mum,  'MEMBER');

  b_web := pg_temp.seed_board(abc, hyd, 'Website Development', 'Company website project', u_harika);
  b_mob := pg_temp.seed_board(abc, hyd, 'Mobile Application Development', 'Customer mobile app', u_harika);
  b_crm := pg_temp.seed_board(abc, blr, 'CRM Development', 'Internal CRM', u_sneha);
  b_ai  := pg_temp.seed_board(abc, blr, 'AI Automation', 'AI assistants and automation', u_sneha);
  b_sup := pg_temp.seed_board(xyz, mum, 'Customer Support Platform', 'Ticketing and support tooling', u_meghana);

  -- Hyderabad: Website Development
  PERFORM pg_temp.seed_task(b_web, 'To Do',       'Create Registration Page', 'Sign-up form with validation',     'MEDIUM', 5,  u_bharath, u_harika);
  PERFORM pg_temp.seed_task(b_web, 'To Do',       'Design Dashboard',         'Wireframes for the dashboard',     'HIGH',   7,  u_harika,  u_harika);
  PERFORM pg_temp.seed_task(b_web, 'To Do',       'Fix API Validation',       'Reject malformed payloads',        'URGENT', -2, u_bharath, u_harika);
  PERFORM pg_temp.seed_task(b_web, 'In Progress', 'Build Contact Form API',   'POST endpoint for contact form',   'MEDIUM', 3,  u_bharath, u_harika);
  PERFORM pg_temp.seed_task(b_web, 'Testing',     'Test Authentication',      'Login and token expiry tests',     'HIGH',   -1, u_charan,  u_harika);
  PERFORM pg_temp.seed_task(b_web, 'Done',        'Deploy Landing Page',      'Published to staging',             'LOW',    NULL, u_bharath, u_harika);
  PERFORM pg_temp.seed_task(b_web, 'Done',        'Set Up CI Pipeline',       'Build and test on every push',     'MEDIUM', NULL, u_harika, u_harika);

  -- Hyderabad: Mobile Application Development
  PERFORM pg_temp.seed_task(b_mob, 'To Do',       'Design App Screens',           'Figma screens for core flows',  'MEDIUM', 10, u_harika,  u_harika);
  PERFORM pg_temp.seed_task(b_mob, 'To Do',       'Set Up React Native Project',  'Project skeleton and linting',  'HIGH',   3,  u_bharath, u_harika);
  PERFORM pg_temp.seed_task(b_mob, 'In Progress', 'Implement Login Screen',       'Email and password login',      'HIGH',   4,  u_charan,  u_harika);
  PERFORM pg_temp.seed_task(b_mob, 'Testing',     'Push Notification Tests',      'Android and iOS delivery',      'MEDIUM', 2,  u_charan,  u_harika);
  PERFORM pg_temp.seed_task(b_mob, 'Done',        'Create App Icon',              'Final icon set exported',       'LOW',    NULL, u_harika, u_harika);

  -- Bangalore: CRM Development
  PERFORM pg_temp.seed_task(b_crm, 'To Do',       'Import Customer Data',         'CSV import with validation',    'HIGH',   6,  u_saikiran, u_sneha);
  PERFORM pg_temp.seed_task(b_crm, 'In Progress', 'Build Lead Pipeline View',     'Kanban view of leads',          'MEDIUM', 8,  u_sneha,    u_sneha);
  PERFORM pg_temp.seed_task(b_crm, 'Done',        'Define CRM Data Model',        'Entities and relationships',    'MEDIUM', NULL, u_sneha,  u_sneha);

  -- Bangalore: AI Automation
  PERFORM pg_temp.seed_task(b_ai,  'To Do',       'Evaluate LLM Providers',       'Compare cost and quality',      'HIGH',   5,  u_saikiran, u_sneha);
  PERFORM pg_temp.seed_task(b_ai,  'In Progress', 'Prototype Support Chatbot',    'Answer FAQs from documents',    'URGENT', 2,  u_sneha,    u_sneha);

  -- Mumbai: Customer Support Platform (other organization)
  PERFORM pg_temp.seed_task(b_sup, 'To Do',       'Set Up Ticket Queues',         'Queues per product line',       'MEDIUM', 4,  u_sunny, u_sunny);
  PERFORM pg_temp.seed_task(b_sup, 'In Progress', 'Integrate Email Channel',      'Create tickets from emails',    'HIGH',   2,  u_sunny, u_sunny);
  PERFORM pg_temp.seed_task(b_sup, 'Done',        'Define SLA Policy',            'Response and resolution times', 'LOW',    NULL, u_sunny, u_sunny);
END $$;

-- ---------- summary ----------

SELECT o.name AS organization, t.name AS tenant,
       count(DISTINCT b.id) AS boards, count(ta.id) AS tasks
FROM organizations o
JOIN tenants t ON t.organization_id = o.id
LEFT JOIN boards b ON b.tenant_id = t.id
LEFT JOIN tasks ta ON ta.board_id = b.id
GROUP BY o.name, t.name
ORDER BY o.name, t.name;
