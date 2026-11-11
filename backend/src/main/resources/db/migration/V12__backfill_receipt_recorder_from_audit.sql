-- Existing receipts may predate user-profile provisioning; recover the actor name from audit.

UPDATE goods_receipt gr
SET recorded_by_name = ae.actor_name
FROM audit_event ae
WHERE gr.recorded_by_name IS NULL
  AND ae.aggregate_type = 'goods_receipt'
  AND ae.aggregate_id = gr.id
  AND ae.action = 'goods_receipt_recorded'
  AND ae.actor_name IS NOT NULL;
