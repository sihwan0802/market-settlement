CREATE UNIQUE INDEX uq_refund_order_item ON refund (order_item_id) WHERE status <> 'FAILED';
