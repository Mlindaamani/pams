-- Global audit access is intended for Auditors and Administrators only.
DELETE FROM role_permission
 WHERE permission_code = 'audit:read'
   AND role_name NOT IN ('ADMIN', 'AUDITOR');
