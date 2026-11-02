# Database ER Diagram

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ TENANTS : "has"
    ORGANIZATIONS ||--o{ MEMBERSHIPS : "has"
    ORGANIZATIONS ||--o{ BOARDS : "owns"
    TENANTS ||--o{ MEMBERSHIPS : "scopes (nullable)"
    TENANTS ||--o{ BOARDS : "has"
    TENANTS ||--o{ COLUMNS : "isolates"
    TENANTS ||--o{ TASKS : "isolates"
    TENANTS ||--o{ ACTIVITY_LOGS : "records"
    USERS ||--o{ MEMBERSHIPS : "joins via"
    USERS ||--o{ BOARDS : "creates"
    USERS ||--o{ TASKS : "creates"
    USERS ||--o{ TASKS : "is assigned"
    USERS ||--o{ ACTIVITY_LOGS : "performs"
    BOARDS ||--o{ COLUMNS : "contains"
    BOARDS ||--o{ TASKS : "contains"
    COLUMNS ||--o{ TASKS : "holds"

    ORGANIZATIONS {
        uuid id PK
        varchar name
        timestamptz created_at
        timestamptz updated_at
    }
    TENANTS {
        uuid id PK
        uuid organization_id FK
        varchar name
        timestamptz created_at
        timestamptz updated_at
    }
    USERS {
        uuid id PK
        varchar name
        varchar email "unique, lowercase"
        varchar password_hash "BCrypt"
        timestamptz created_at
    }
    MEMBERSHIPS {
        uuid id PK
        uuid user_id FK
        uuid organization_id FK
        uuid tenant_id FK "NULL = org-level admin"
        membership_role role "ORG_ADMIN, TENANT_ADMIN, MEMBER"
        timestamptz created_at
    }
    BOARDS {
        uuid id PK
        uuid organization_id FK
        uuid tenant_id FK
        varchar name
        text description
        uuid created_by FK
        int version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
    COLUMNS {
        uuid id PK
        uuid board_id FK
        uuid tenant_id FK
        varchar name "unique per board"
        text position "fractional rank"
        int version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
    TASKS {
        uuid id PK
        uuid board_id FK
        uuid column_id FK
        uuid tenant_id FK
        varchar title
        text description
        task_priority priority "LOW, MEDIUM, HIGH, URGENT"
        timestamptz due_date
        uuid assigned_to FK
        uuid created_by FK
        text position "fractional rank"
        int version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
    ACTIVITY_LOGS {
        uuid id PK
        uuid tenant_id FK
        uuid user_id FK
        varchar entity_type "TASK, BOARD, COLUMN"
        uuid entity_id
        varchar action "CREATED, UPDATED, MOVED, DELETED"
        jsonb details
        timestamptz created_at
    }
```

## Design notes

**Multi-tenancy.** An organization contains tenants (for example ABC Technologies has Hyderabad, Bangalore and Chennai). Every tenant-owned table (`boards`, `columns`, `tasks`, `activity_logs`) carries a `tenant_id`. PostgreSQL Row Level Security policies filter rows with the `app.current_tenant` setting, which the backend sets inside each transaction (`TenantContext.apply`). Even a buggy query cannot read another tenant's data.

**Roles.** `memberships.role` is one of `ORG_ADMIN`, `TENANT_ADMIN`, `MEMBER`. An organization-level admin has a membership row with `tenant_id = NULL` and can access every tenant in that organization. Tenant admins and members have a membership tied to one tenant.

**Optimistic locking.** `boards`, `columns` and `tasks` have a `version` column. Update and move requests send `expectedVersion`; a mismatch returns HTTP 409 and the UI reloads the latest data.

**Ordering.** `columns.position` and `tasks.position` are zero-padded text ranks (for example `0001000`). A drag and drop inserts a rank between the previous and next neighbour, so reordering never rewrites the whole list.

**Audit trail.** `activity_logs` stores who did what (`entity_type`, `action`, JSON `details`), written in the same transaction as the change, and powers the Activity panel.
