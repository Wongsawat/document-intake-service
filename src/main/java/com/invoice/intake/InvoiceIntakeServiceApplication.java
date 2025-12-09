package com.invoice.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Invoice Intake Service - Main Application
 *
 * This microservice acts as the gateway for XML invoices entering the system.
 *
 * Key Features:
 * - Receives XML invoices via REST API and Kafka
 * - Validates XML against XSD schema using teda library
 * - Extracts invoice metadata
 * - Publishes validated invoices to downstream services
 * - Apache Camel integration for flexible routing
 *
 * @author wpanther
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
public class InvoiceIntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceIntakeServiceApplication.class, args);
    }
}
