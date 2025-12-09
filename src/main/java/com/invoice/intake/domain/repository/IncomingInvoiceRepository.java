package com.invoice.intake.domain.repository;

import com.invoice.intake.domain.model.IncomingInvoice;
import com.invoice.intake.domain.model.InvoiceStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for IncomingInvoice aggregate
 */
public interface IncomingInvoiceRepository {

    /**
     * Save an incoming invoice
     */
    IncomingInvoice save(IncomingInvoice invoice);

    /**
     * Find invoice by ID
     */
    Optional<IncomingInvoice> findById(UUID id);

    /**
     * Find invoice by invoice number
     */
    Optional<IncomingInvoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find invoices by status
     */
    List<IncomingInvoice> findByStatus(InvoiceStatus status);

    /**
     * Check if invoice number exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);
}
