delete from role_permission
 where role_name = 'ADMIN'
   and permission_code in ('platform:tenant_read', 'platform:tenant_manage', 'platform:analytics_read');
