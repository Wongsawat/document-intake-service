package com.invoice.intake.infrastructure.persistence;

import com.invoice.intake.domain.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for IncomingInvoiceEntity
 */
@Repository
public interface JpaIncomingInvoiceRepository extends JpaRepository<IncomingInvoiceEntity, UUID> {

    /**
     * Find by invoice number
     */
    Optional<IncomingInvoiceEntity> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find by status
     */
    List<IncomingInvoiceEntity> findByStatus(InvoiceStatus status);

    /**
     * Check if invoice number exists
     */
    boolean existsByInvoiceNumber(String invoiceNumber);
}
