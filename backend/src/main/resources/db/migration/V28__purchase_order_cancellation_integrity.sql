-- Allow a new purchase order after a previous draft order was cancelled.
DROP INDEX uq_purchase_order_request;
CREATE UNIQUE INDEX uq_purchase_order_active_request
    ON purchase_order (purchase_request_id)
    WHERE status <> 'CANCELLED';
