-- =====================================================================
-- Real-Time Collaborative Kanban: multi-organization, multi-tenant schema
-- PostgreSQL 14+
--
-- Isolation is enforced in THREE layers:
--   1. Application layer : JWT -> membership check -> tenant-scoped queries
--   2. Schema layer      : composite foreign keys, so a child row can never
--                          point at a parent that belongs to another tenant
--   3. Row-Level Security: the DB itself hides rows outside app.current_tenant
-- =====================================================================

-- ---------- Enums ----------------------------------------------------
CREATE TYPE membership_role AS ENUM ('ORG_ADMIN', 'TENANT_ADMIN', 'MEMBER');
CREATE TYPE task_priority   AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'URGENT');

-- ---------- Helper: keep updated_at fresh -----------------------------
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------- 1. organizations -----------------------------------------
CREATE TABLE organizations (
  id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  name        varchar(150) NOT NULL,
  created_at  timestamptz  NOT NULL DEFAULT now(),
  updated_at  timestamptz  NOT NULL DEFAULT now()
);

-- ---------- 2. tenants (branches) ------------------------------------
CREATE TABLE tenants (
  id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name            varchar(150) NOT NULL,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name),
  UNIQUE (id, organization_id)            -- target for composite FKs
);
CREATE INDEX idx_tenants_org ON tenants (organization_id);

-- ---------- 3. users --------------------------------------------------
CREATE TABLE users (
  id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  name          varchar(150) NOT NULL,
  email         varchar(255) NOT NULL,
  password_hash varchar(255) NOT NULL,
  created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

-- ---------- 4. memberships -------------------------------------------
-- ORG_ADMIN    -> tenant_id IS NULL  (access to every tenant of the org)
-- TENANT_ADMIN -> tenant_id NOT NULL (access to one tenant)
-- MEMBER       -> tenant_id NOT NULL
CREATE TABLE memberships (
  id              uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id uuid            NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  tenant_id       uuid,
  role            membership_role NOT NULL,
  created_at      timestamptz     NOT NULL DEFAULT now(),
  -- when tenant_id is set, it must belong to the SAME organization
  FOREIGN KEY (tenant_id, organization_id)
    REFERENCES tenants (id, organization_id) ON DELETE CASCADE,
  CHECK (
    (role = 'ORG_ADMIN' AND tenant_id IS NULL) OR
    (role <> 'ORG_ADMIN' AND tenant_id IS NOT NULL)
  )
);
CREATE UNIQUE INDEX uq_membership_org_level    ON memberships (user_id, organization_id) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uq_membership_tenant_level ON memberships (user_id, tenant_id)       WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_memberships_user   ON memberships (user_id);
CREATE INDEX idx_memberships_tenant ON memberships (tenant_id);
CREATE INDEX idx_memberships_org    ON memberships (organization_id);

-- ---------- 5. boards -------------------------------------------------
CREATE TABLE boards (
  id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid         NOT NULL,
  tenant_id       uuid         NOT NULL,
  name            varchar(150) NOT NULL,
  description     text,
  created_by      uuid         REFERENCES users(id) ON DELETE SET NULL,
  version         integer      NOT NULL DEFAULT 1,
  created_at      timestamptz  NOT NULL DEFAULT now(),
  updated_at      timestamptz  NOT NULL DEFAULT now(),
  FOREIGN KEY (tenant_id, organization_id)
    REFERENCES tenants (id, organization_id) ON DELETE CASCADE,
  UNIQUE (id, tenant_id)
);
CREATE INDEX idx_boards_tenant ON boards (tenant_id);

-- ---------- 6. columns ------------------------------------------------
-- position is a fractional-index string (e.g. 'a0', 'a0V', 'a1').
-- COLLATE "C" gives plain byte ordering, required by fractional indexing.
CREATE TABLE columns (
  id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  board_id   uuid         NOT NULL,
  tenant_id  uuid         NOT NULL,
  name       varchar(100) NOT NULL,
  position   text COLLATE "C" NOT NULL,
  version    integer      NOT NULL DEFAULT 1,
  created_at timestamptz  NOT NULL DEFAULT now(),
  updated_at timestamptz  NOT NULL DEFAULT now(),
  FOREIGN KEY (board_id, tenant_id)
    REFERENCES boards (id, tenant_id) ON DELETE CASCADE,
  UNIQUE (id, board_id, tenant_id)        -- target for tasks composite FK
);
CREATE INDEX idx_columns_board_pos ON columns (board_id, position, id);
CREATE INDEX idx_columns_tenant    ON columns (tenant_id);

-- ---------- 7. tasks --------------------------------------------------
CREATE TABLE tasks (
  id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
  board_id    uuid          NOT NULL,
  column_id   uuid          NOT NULL,
  tenant_id   uuid          NOT NULL,
  title       varchar(255)  NOT NULL,
  description text,
  priority    task_priority NOT NULL DEFAULT 'MEDIUM',
  due_date    timestamptz,
  assigned_to uuid          REFERENCES users(id) ON DELETE SET NULL,
  created_by  uuid          REFERENCES users(id) ON DELETE SET NULL,
  position    text COLLATE "C" NOT NULL,
  version     integer       NOT NULL DEFAULT 1,   -- optimistic locking
  created_at  timestamptz   NOT NULL DEFAULT now(),
  updated_at  timestamptz   NOT NULL DEFAULT now(),
  -- ONE composite FK guarantees: column exists AND belongs to this board
  -- AND belongs to this tenant. Cross-tenant task->column links are impossible.
  FOREIGN KEY (column_id, board_id, tenant_id)
    REFERENCES columns (id, board_id, tenant_id) ON DELETE CASCADE
);
CREATE INDEX idx_tasks_tenant       ON tasks (tenant_id);
CREATE INDEX idx_tasks_board        ON tasks (board_id);
CREATE INDEX idx_tasks_column_order ON tasks (column_id, position, id);
CREATE INDEX idx_tasks_assignee     ON tasks (assigned_to);

-- Assignee must be a member of the task's tenant (or admin of its org).
CREATE FUNCTION check_assignee_in_tenant() RETURNS trigger AS $$
BEGIN
  IF NEW.assigned_to IS NULL THEN
    RETURN NEW;
  END IF;
  IF NOT EXISTS (
    SELECT 1
    FROM memberships m
    JOIN tenants t ON t.id = NEW.tenant_id
    WHERE m.user_id = NEW.assigned_to
      AND m.organization_id = t.organization_id
      AND (m.tenant_id = NEW.tenant_id OR m.tenant_id IS NULL)
  ) THEN
    RAISE EXCEPTION 'assignee % is not a member of tenant %', NEW.assigned_to, NEW.tenant_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tasks_assignee
  BEFORE INSERT OR UPDATE OF assigned_to ON tasks
  FOR EACH ROW EXECUTE FUNCTION check_assignee_in_tenant();

-- ---------- 8. activity_logs ------------------------------------------
-- details = { "before": {...}, "after": {...} }  -> powers the activity feed
-- and undo/redo (inverse operation is computed from before/after).
CREATE TABLE activity_logs (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   uuid        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id     uuid        REFERENCES users(id) ON DELETE SET NULL,
  entity_type varchar(30) NOT NULL,      -- 'task' | 'column' | 'board'
  entity_id   uuid        NOT NULL,
  action      varchar(30) NOT NULL,      -- created | updated | moved | deleted | undone
  details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
  request_id  uuid,                      -- client operation id (idempotency)
  undo_of     uuid        REFERENCES activity_logs(id) ON DELETE SET NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_tenant_time ON activity_logs (tenant_id, created_at DESC);
CREATE INDEX idx_activity_entity      ON activity_logs (tenant_id, entity_type, entity_id);
CREATE UNIQUE INDEX uq_activity_request ON activity_logs (tenant_id, request_id) WHERE request_id IS NOT NULL;

-- ---------- updated_at triggers ---------------------------------------
CREATE TRIGGER trg_org_updated     BEFORE UPDATE ON organizations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tenants_updated BEFORE UPDATE ON tenants       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_boards_updated  BEFORE UPDATE ON boards        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_columns_updated BEFORE UPDATE ON columns       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tasks_updated   BEFORE UPDATE ON tasks         FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- Row-Level Security (defence in depth)
-- The backend runs each tenant request inside a transaction that does:
--   SELECT set_config('app.current_tenant', '<tenant uuid>', true);
-- If the setting is missing, NULLIF(...) yields NULL, the policy evaluates
-- to NULL and NO rows are visible (fails closed).
-- Tables outside RLS (organizations, tenants, users, memberships) are read
-- during login / tenant selection, before a tenant context exists; they are
-- guarded by the application layer.
-- =====================================================================
CREATE FUNCTION current_tenant_id() RETURNS uuid AS $$
  SELECT NULLIF(current_setting('app.current_tenant', true), '')::uuid;
$$ LANGUAGE sql STABLE;

ALTER TABLE boards        ENABLE ROW LEVEL SECURITY;
ALTER TABLE columns       ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks         ENABLE ROW LEVEL SECURITY;
ALTER TABLE activity_logs ENABLE ROW LEVEL SECURITY;
-- FORCE applies the policies to the table owner too (superusers still bypass,
-- so the app must connect as the non-superuser role created below).
ALTER TABLE boards        FORCE ROW LEVEL SECURITY;
ALTER TABLE columns       FORCE ROW LEVEL SECURITY;
ALTER TABLE tasks         FORCE ROW LEVEL SECURITY;
ALTER TABLE activity_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON boards
  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY tenant_isolation ON columns
  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY tenant_isolation ON tasks
  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY tenant_isolation ON activity_logs
  USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

-- ---------- Application role (non-superuser, subject to RLS) ----------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kanban_app') THEN
    CREATE ROLE kanban_app LOGIN PASSWORD 'change_me_in_env';
  END IF;
END $$;
GRANT USAGE ON SCHEMA public TO kanban_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kanban_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO kanban_app;
