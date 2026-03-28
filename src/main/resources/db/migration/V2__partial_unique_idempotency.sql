-- Drop the absolute unique constraint on idempotency_key
ALTER TABLE rides DROP CONSTRAINT IF EXISTS rides_idempotency_key_key;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_idempotency_key_key;

-- Add partial unique index: only enforce for active (non-terminal) rides
CREATE UNIQUE INDEX idx_rides_idempotency_active
  ON rides(idempotency_key)
  WHERE status_id NOT IN (206, 207) AND delete_info IS NULL;

-- Same for payments: only enforce for non-terminal payments
CREATE UNIQUE INDEX idx_payments_idempotency_active
  ON payments(idempotency_key)
  WHERE status_id NOT IN (403, 404, 405) AND delete_info IS NULL;
