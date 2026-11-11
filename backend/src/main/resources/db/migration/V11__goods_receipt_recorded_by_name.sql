-- Show a human-readable recorder instead of the Keycloak subject UUID.

ALTER TABLE goods_receipt ADD COLUMN recorded_by_name VARCHAR(200);

UPDATE goods_receipt gr
SET recorded_by_name = up.display_name
FROM user_profile up
WHERE up.organization_id = gr.organization_id
  AND up.keycloak_subject = gr.created_by;
