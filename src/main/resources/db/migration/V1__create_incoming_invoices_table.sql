-- Create incoming_invoices table
CREATE TABLE incoming_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL,
    xml_content TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    validation_result JSONB,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_incoming_invoice_number ON incoming_invoices(invoice_number);
CREATE INDEX idx_incoming_status ON incoming_invoices(status);
CREATE INDEX idx_incoming_received ON incoming_invoices(received_at);

-- Add unique constraint on invoice number
CREATE UNIQUE INDEX idx_incoming_invoice_number_unique ON incoming_invoices(invoice_number);
