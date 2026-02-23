package com.wpanther.document.intake.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for document validation limits.
 * <p>
 * Properties are loaded from app.validation.* prefix in application.yml:
 * - max-xml-size: Maximum XML document size in bytes
 * - max-xml-depth: Maximum allowed element nesting depth
 * - max-element-count: Maximum number of elements allowed
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.validation")
public class ValidationProperties {

    /**
     * Maximum XML document size in bytes.
     * Default: 10MB (10,485,760 bytes)
     */
    private long maxXmlSize = 10_485_760L;

    /**
     * Maximum allowed element nesting depth.
     * Helps prevent billion laughs attack through deeply nested elements.
     * Default: 100
     */
    private int maxXmlDepth = 100;

    /**
     * Maximum number of elements allowed in XML document.
     * Helps prevent DoS through excessive element count.
     * Default: 10,000
     */
    private int maxElementCount = 10_000;

    /**
     * Get maximum XML size in megabytes.
     */
    public int getMaxXmlSizeMb() {
        return (int) (maxXmlSize / (1024 * 1024));
    }
}
