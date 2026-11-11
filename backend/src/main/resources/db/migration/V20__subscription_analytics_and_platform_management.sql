alter table tenant_subscription
    add column activated_at timestamptz;

update tenant_subscription
   set activated_at = coalesce(current_period_start, updated_at, created_at)
 where status = 'ACTIVE';

create table subscription_mrr_snapshot (
    id uuid primary key default gen_random_uuid(),
    snapshot_date date not null unique,
    mrr numeric(19, 4) not null,
    arr numeric(19, 4) not null,
    paying_tenants integer not null,
    trialing_tenants integer not null,
    past_due_tenants integer not null,
    canceled_tenants integer not null,
    created_at timestamptz not null default now(),
    created_by varchar(100) not null default 'system'
);

delete from role_permission
 where role_name = 'PLATFORM_ADMIN'
   and permission_code in ('subscription:read', 'subscription:manage', 'webhook:manage');
