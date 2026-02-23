-- ============================================================
-- Document Intake Service - Timestamps with timezone
-- Converts TIMESTAMP columns to TIMESTAMP WITH TIME ZONE
-- for correct UTC storage and timezone-aware handling.
-- ============================================================

ALTER TABLE incoming_documents
    ALTER COLUMN received_at  TYPE TIMESTAMP WITH TIME ZONE USING received_at  AT TIME ZONE 'UTC',
    ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE USING processed_at  AT TIME ZONE 'UTC',
    ALTER COLUMN created_at   TYPE TIMESTAMP WITH TIME ZONE USING created_at   AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at   TYPE TIMESTAMP WITH TIME ZONE USING updated_at   AT TIME ZONE 'UTC';

ALTER TABLE outbox_events
    ALTER COLUMN created_at   TYPE TIMESTAMP WITH TIME ZONE USING created_at   AT TIME ZONE 'UTC',
    ALTER COLUMN published_at TYPE TIMESTAMP WITH TIME ZONE USING published_at AT TIME ZONE 'UTC';
