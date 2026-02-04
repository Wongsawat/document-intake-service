-- ============================================================
-- Document Intake Service - Baseline Schema
-- Single consolidated migration for development environment
-- ============================================================

-- Main table for storing incoming XML documents
CREATE TABLE incoming_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL,
    xml_content TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    validation_result TEXT,
    document_type VARCHAR(50),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for incoming_invoices
CREATE INDEX idx_incoming_invoice_number ON incoming_invoices(invoice_number);
CREATE INDEX idx_incoming_status ON incoming_invoices(status);
CREATE INDEX idx_incoming_received ON incoming_invoices(received_at);
CREATE INDEX idx_incoming_document_type ON incoming_invoices(document_type);
CREATE UNIQUE INDEX idx_incoming_invoice_number_unique ON incoming_invoices(invoice_number);

-- Comments for incoming_invoices
COMMENT ON COLUMN incoming_invoices.document_type IS 'Thai e-Tax document type (TAX_INVOICE, RECEIPT, INVOICE, DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE)';
COMMENT ON COLUMN incoming_invoices.validation_result IS 'Validation result as JSON text (portable across databases)';

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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);

-- Indexes for outbox_events
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Comments for outbox_events
COMMENT ON COLUMN outbox_events.payload IS 'Event payload as JSON text (portable across databases)';
COMMENT ON COLUMN outbox_events.headers IS 'Kafka headers as JSON text (portable across databases)';
