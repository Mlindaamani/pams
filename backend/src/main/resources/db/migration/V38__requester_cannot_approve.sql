-- Requesters prepare and submit requests; they do not participate in the approval inbox.
DELETE FROM role_permission
 WHERE role_name = 'REQUESTER'
   AND permission_code IN ('approval:read', 'approval:act', 'approval:act:REQUESTER');
