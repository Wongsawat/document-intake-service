-- Add document_type column to incoming_invoices table
ALTER TABLE incoming_invoices
ADD COLUMN document_type VARCHAR(50);

-- Add index for filtering by document type
CREATE INDEX idx_incoming_document_type
ON incoming_invoices(document_type);

-- Add comment
COMMENT ON COLUMN incoming_invoices.document_type IS 'Thai e-Tax document type (TAX_INVOICE, RECEIPT, INVOICE, DEBIT_CREDIT_NOTE, CANCELLATION_NOTE, ABBREVIATED_TAX_INVOICE)';
