-- ============================================================
-- Document Intake Service - Baseline Schema
-- Single consolidated migration for development environment
-- ============================================================

-- Main table for storing incoming XML documents
CREATE TABLE incoming_documents (
    id UUID PRIMARY KEY,
    document_number VARCHAR(50) NOT NULL,
    xml_content TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    validation_result TEXT,
    document_type VARCHAR(50),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for incoming_documents
CREATE INDEX idx_incoming_document_number ON incoming_documents(document_number);
CREATE INDEX idx_incoming_status ON incoming_documents(status);
CREATE INDEX idx_incoming_received ON incoming_documents(received_at);
CREATE INDEX idx_incoming_document_type ON incoming_documents(document_type);
CREATE UNIQUE INDEX idx_incoming_document_number_unique ON incoming_documents(document_number);

-- Comments for incoming_documents
COMMENT ON COLUMN incoming_documents.document_type IS 'Thai e-Tax document type (TAX_INVOICE, RECEIPT, INVOICE, DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE)';
COMMENT ON COLUMN incoming_documents.validation_result IS 'Validation result as JSON text (portable across databases)';
COMMENT ON COLUMN incoming_documents.received_at IS 'Document receipt timestamp (UTC)';
COMMENT ON COLUMN incoming_documents.processed_at IS 'Document processing completion timestamp (UTC)';
COMMENT ON COLUMN incoming_documents.created_at IS 'Record creation timestamp (UTC)';
COMMENT ON COLUMN incoming_documents.updated_at IS 'Record last update timestamp (UTC)';

-- Outbox pattern table for reliable event publishing (Debezium CDC)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for outbox_events
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Comments for outbox_events
COMMENT ON COLUMN outbox_events.payload IS 'Event payload as JSON text (portable across databases)';
COMMENT ON COLUMN outbox_events.headers IS 'Kafka headers as JSON text (portable across databases)';
COMMENT ON COLUMN outbox_events.created_at IS 'Event creation timestamp (UTC)';
COMMENT ON COLUMN outbox_events.published_at IS 'Event publication timestamp (UTC)';
