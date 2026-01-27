package com.wpanther.document.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Document Intake Service - Main Application
 *
 * This microservice acts as the gateway for XML documents entering the system.
 *
 * Key Features:
 * - Receives XML documents via REST API and Kafka
 * - Validates XML against XSD schema using teda library
 * - Extracts document metadata
 * - Publishes validated documents to downstream services
 * - Apache Camel integration for flexible routing
 *
 * @author wpanther
 * @version 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
public class DocumentIntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentIntakeServiceApplication.class, args);
    }
}
