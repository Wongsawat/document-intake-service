package com.wpanther.document.intake.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ValidationProperties
 */
@DisplayName("ValidationProperties Unit Tests")
class ValidationPropertiesTest {

    private ValidationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ValidationProperties();
    }

    @Test
    @DisplayName("Default values are set correctly")
    void testDefaultValues() {
        assertThat(properties.getMaxXmlSize()).isEqualTo(10_485_760L); // 10MB
        assertThat(properties.getMaxXmlDepth()).isEqualTo(100);
        assertThat(properties.getMaxElementCount()).isEqualTo(10_000);
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(10);
    }

    @Test
    @DisplayName("Setter and getter for max XML size")
    void testMaxXmlSizeSetterGetter() {
        properties.setMaxXmlSize(5_242_880L); // 5MB
        assertThat(properties.getMaxXmlSize()).isEqualTo(5_242_880L);
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(5);

        properties.setMaxXmlSize(20_971_520L); // 20MB
        assertThat(properties.getMaxXmlSize()).isEqualTo(20_971_520L);
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(20);
    }

    @Test
    @DisplayName("Setter and getter for max XML depth")
    void testMaxXmlDepthSetterGetter() {
        properties.setMaxXmlDepth(50);
        assertThat(properties.getMaxXmlDepth()).isEqualTo(50);

        properties.setMaxXmlDepth(200);
        assertThat(properties.getMaxXmlDepth()).isEqualTo(200);
    }

    @Test
    @DisplayName("Setter and getter for max element count")
    void testMaxElementCountSetterGetter() {
        properties.setMaxElementCount(5_000);
        assertThat(properties.getMaxElementCount()).isEqualTo(5_000);

        properties.setMaxElementCount(20_000);
        assertThat(properties.getMaxElementCount()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("getMaxXmlSizeMb calculates correct MB value")
    void testGetMaxXmlSizeMbCalculatesCorrectly() {
        properties.setMaxXmlSize(1_048_576L); // 1MB
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(1);

        properties.setMaxXmlSize(15_728_640L); // 15MB
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(15);

        properties.setMaxXmlSize(104_857_600L); // 100MB
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(100);
    }

    @Test
    @DisplayName("Multiple properties can be set together")
    void testMultiplePropertiesSetTogether() {
        properties.setMaxXmlSize(5_242_880L);
        properties.setMaxXmlDepth(50);
        properties.setMaxElementCount(5_000);

        assertThat(properties.getMaxXmlSize()).isEqualTo(5_242_880L);
        assertThat(properties.getMaxXmlDepth()).isEqualTo(50);
        assertThat(properties.getMaxElementCount()).isEqualTo(5_000);
    }

    @ParameterizedTest
    @DisplayName("getMaxXmlSizeMb handles various sizes correctly")
    @ValueSource(longs = {1_048_576L, 2_097_152L, 5_242_880L, 10_485_760L, 20_971_520L, 104_857_600L})
    void testGetMaxXmlSizeMbHandlesVariousSizes(long sizeInBytes) {
        properties.setMaxXmlSize(sizeInBytes);
        int expectedMb = (int) (sizeInBytes / (1024 * 1024));
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(expectedMb);
    }

    @Test
    @DisplayName("Properties handle zero values")
    void testPropertiesHandleZeroValues() {
        properties.setMaxXmlSize(0);
        properties.setMaxXmlDepth(0);
        properties.setMaxElementCount(0);

        assertThat(properties.getMaxXmlSize()).isZero();
        assertThat(properties.getMaxXmlDepth()).isZero();
        assertThat(properties.getMaxElementCount()).isZero();
    }

    @Test
    @DisplayName("Properties handle large values")
    void testPropertiesHandleLargeValues() {
        properties.setMaxXmlSize(1_073_741_824L); // 1GB
        properties.setMaxXmlDepth(1000);
        properties.setMaxElementCount(1_000_000);

        assertThat(properties.getMaxXmlSize()).isEqualTo(1_073_741_824L);
        assertThat(properties.getMaxXmlDepth()).isEqualTo(1000);
        assertThat(properties.getMaxElementCount()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("getMaxXmlSizeMb returns 0 for size less than 1MB")
    void testGetMaxXmlSizeMbForSizeLessThan1MB() {
        properties.setMaxXmlSize(500_000L);
        assertThat(properties.getMaxXmlSizeMb()).isEqualTo(0);
    }
}
