package com.wpanther.document.intake.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.document.intake.domain.exception.ValidationResultSerializationException;
import com.wpanther.document.intake.domain.model.DocumentStatus;
import com.wpanther.document.intake.domain.model.IncomingDocument;
import com.wpanther.document.intake.domain.model.ValidationResult;
import com.wpanther.document.intake.infrastructure.validation.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncomingDocumentRepositoryImpl Tests")
class IncomingDocumentRepositoryImplTest {

    @Mock
    private JpaIncomingDocumentRepository jpaRepository;

    @Mock
    private ObjectMapper objectMapper;

    private IncomingDocumentRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new IncomingDocumentRepositoryImpl(jpaRepository, objectMapper);
    }

    @Test
    @DisplayName("Should save incoming document")
    void shouldSaveDocument() throws JsonProcessingException {
        IncomingDocument document = IncomingDocument.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.RECEIVED)
                .build();

        IncomingDocumentEntity entity = toEntity(document);
        when(jpaRepository.save(any(IncomingDocumentEntity.class))).thenReturn(entity);

        IncomingDocument result = repository.save(document);

        assertThat(result).isNotNull();
        verify(jpaRepository).save(any(IncomingDocumentEntity.class));
    }

    @Test
    @DisplayName("Should save document with validation result")
    void shouldSaveDocumentWithValidationResult() throws JsonProcessingException {
        ValidationResult validationResult = ValidationResult.success();
        IncomingDocument document = IncomingDocument.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.VALIDATED)
                .validationResult(validationResult)
                .build();

        IncomingDocumentEntity entity = toEntity(document);
        when(jpaRepository.save(any(IncomingDocumentEntity.class))).thenReturn(entity);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        IncomingDocument result = repository.save(document);

        assertThat(result).isNotNull();
        verify(objectMapper).writeValueAsString(validationResult);
    }

    @Test
    @DisplayName("Should throw exception when validation result serialization fails")
    void shouldThrowExceptionWhenValidationResultSerializationFails() throws JsonProcessingException {
        ValidationResult validationResult = ValidationResult.success();
        IncomingDocument document = IncomingDocument.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.VALIDATED)
                .validationResult(validationResult)
                .build();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {});

        assertThatThrownBy(() -> repository.save(document))
                .isInstanceOf(ValidationResultSerializationException.class)
                .hasMessageContaining("Failed to serialize ValidationResult to JSON");
    }

    @Test
    @DisplayName("Should find document by id")
    void shouldFindById() throws JsonProcessingException {
        UUID id = UUID.randomUUID();
        IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(id)
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.RECEIVED)
                .build();

        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<IncomingDocument> result = repository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("Should return empty when not found by id")
    void shouldReturnEmptyWhenNotFoundById() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        Optional<IncomingDocument> result = repository.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find document by document number")
    void shouldFindByDocumentNumber() throws JsonProcessingException {
        String documentNumber = "INV-001";
        IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber(documentNumber)
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.RECEIVED)
                .build();

        when(jpaRepository.findByDocumentNumber(documentNumber)).thenReturn(Optional.of(entity));

        Optional<IncomingDocument> result = repository.findByDocumentNumber(documentNumber);

        assertThat(result).isPresent();
        assertThat(result.get().getDocumentNumber()).isEqualTo(documentNumber);
    }

    @Test
    @DisplayName("Should find documents by status")
    void shouldFindByStatus() throws JsonProcessingException {
        List<IncomingDocumentEntity> entities = List.of(
                createEntity(DocumentStatus.RECEIVED),
                createEntity(DocumentStatus.RECEIVED)
        );

        when(jpaRepository.findByStatus(DocumentStatus.RECEIVED)).thenReturn(entities);

        List<IncomingDocument> result = repository.findByStatus(DocumentStatus.RECEIVED);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Should check if document exists by number")
    void shouldCheckExistsByDocumentNumber() {
        String documentNumber = "INV-001";
        when(jpaRepository.existsByDocumentNumber(documentNumber)).thenReturn(true);

        boolean result = repository.existsByDocumentNumber(documentNumber);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should deserialize validation result from entity")
    void shouldDeserializeValidationResultFromEntity() throws Exception {
        UUID id = UUID.randomUUID();
        ValidationResult validationResult = ValidationResult.success();
        String json = "{\"valid\":true,\"errors\":[],\"warnings\":[]}";

        IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(id)
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.VALIDATED)
                .validationResult(json)
                .build();

        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(json, ValidationResult.class)).thenReturn(validationResult);

        Optional<IncomingDocument> result = repository.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getValidationResult()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when validation result deserialization fails")
    void shouldThrowExceptionWhenValidationResultDeserializationFails() throws Exception {
        UUID id = UUID.randomUUID();
        String json = "{\"invalid\":json}";

        IncomingDocumentEntity entity = IncomingDocumentEntity.builder()
                .id(id)
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(DocumentStatus.VALIDATED)
                .validationResult(json)
                .build();

        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(json, ValidationResult.class)).thenThrow(new JsonProcessingException("Deserialization failed") {});

        assertThatThrownBy(() -> repository.findById(id))
                .isInstanceOf(ValidationResultSerializationException.class)
                .hasMessageContaining("Failed to deserialize ValidationResult from JSON");
    }

    private IncomingDocumentEntity toEntity(IncomingDocument document) {
        return IncomingDocumentEntity.builder()
                .id(document.getId())
                .documentNumber(document.getDocumentNumber())
                .xmlContent(document.getXmlContent())
                .source(document.getSource())
                .correlationId(document.getCorrelationId())
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .build();
    }

    private IncomingDocumentEntity createEntity(DocumentStatus status) {
        return IncomingDocumentEntity.builder()
                .id(UUID.randomUUID())
                .documentNumber("INV-001")
                .xmlContent("<xml></xml>")
                .source("API")
                .correlationId("corr-123")
                .documentType(DocumentType.TAX_INVOICE)
                .status(status)
                .build();
    }
}
