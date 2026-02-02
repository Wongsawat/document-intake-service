-- Add columns needed for Debezium Outbox Event Router
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS topic VARCHAR(255);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS partition_key VARCHAR(255);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS headers JSONB;

-- Add index for Debezium CDC polling (optimized for pending events)
CREATE INDEX IF NOT EXISTS idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';

-- Update any existing rows (for completeness - existing rows would be from testing)
UPDATE outbox_events SET topic = 'document.received' WHERE topic IS NULL;
