-- Read-only evidence for the RLS inventory test.
-- Run: docker compose exec -T postgres psql -U pams -d pams -f - < ops/postgres/rls-inventory.sql
select col.table_name,
       cls.relrowsecurity as rls_enabled,
       cls.relforcerowsecurity as rls_forced,
       exists (
         select 1 from pg_policies policy
         where policy.schemaname = 'public'
           and policy.tablename = col.table_name
           and policy.policyname = col.table_name || '_tenant_isolation'
       ) as has_policy,
       case
         when col.table_name in (
           'outbox_event',
           'tenant_provisioning',
           'tenant_subscription',
           'webhook_delivery',
           'webhook_subscription'
         ) then 'documented exemption'
         when cls.relrowsecurity and cls.relforcerowsecurity then 'ok'
         else 'MISSING'
       end as status
  from information_schema.columns col
  join pg_class cls
    on cls.relname = col.table_name
   and cls.relnamespace = 'public'::regnamespace
 where col.table_schema = 'public'
   and col.column_name = 'organization_id'
 order by col.table_name;
