-- Convert validation_result from JSONB to TEXT for portability
-- This migration preserves existing data and makes the code database-agnostic

-- Step 1: Add new TEXT column for validation_result
ALTER TABLE incoming_invoices ADD COLUMN IF NOT EXISTS validation_result_new TEXT;

-- Step 2: Migrate existing data (convert JSONB to TEXT)
UPDATE incoming_invoices
SET validation_result_new = validation_result::TEXT
WHERE validation_result IS NOT NULL;

-- Step 3: Set default value for new column
ALTER TABLE incoming_invoices
ALTER COLUMN validation_result_new SET DEFAULT '{}';

-- Step 4: Drop old JSONB column
ALTER TABLE incoming_invoices DROP COLUMN validation_result;

-- Step 5: Rename new column to original name
ALTER TABLE incoming_invoices RENAME COLUMN validation_result_new TO validation_result;

-- Step 6: Add comment
COMMENT ON COLUMN incoming_invoices.validation_result IS 'Validation result as JSON text (portable across databases)';

-- ==================== Also convert outbox_events headers/payload for portability ====================

-- Step 7: Convert headers from JSONB to TEXT
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS headers_new TEXT;
UPDATE outbox_events SET headers_new = headers::TEXT WHERE headers IS NOT NULL;
ALTER TABLE outbox_events DROP COLUMN headers;
ALTER TABLE outbox_events RENAME COLUMN headers_new TO headers;
COMMENT ON COLUMN outbox_events.headers IS 'Kafka headers as JSON text (portable across databases)';

-- Step 8: Convert payload from JSONB to TEXT
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS payload_new TEXT;
UPDATE outbox_events SET payload_new = payload::TEXT WHERE payload IS NOT NULL;
ALTER TABLE outbox_events DROP COLUMN payload;
ALTER TABLE outbox_events RENAME COLUMN payload_new TO payload;
COMMENT ON COLUMN outbox_events.payload IS 'Event payload as JSON text (portable across databases)';
