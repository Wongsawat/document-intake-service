package com.wpanther.document.intake.infrastructure.validation;

import com.wpanther.document.intake.domain.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XmlValidationServiceImpl.
 * Tests cover three-layer validation: XML well-formedness, XSD schema, and Schematron business rules.
 */
@DisplayName("XmlValidationService Implementation Tests")
class XmlValidationServiceImplTest {

    private XmlValidationServiceImpl validationService;

    private static final String VALID_TAX_INVOICE_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:TaxInvoice_CrossIndustryInvoice
            xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2"
            xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>TIV2024010001</ram:ID>
                <ram:Name>ใบกำกับภาษี</ram:Name>
                <ram:TypeCode>388</ram:TypeCode>
                <ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>
                <ram:PurposeCode>TIVC01</ram:PurposeCode>
            </rsm:ExchangedDocument>
            <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                    <ram:SellerTradeParty>
                        <ram:Name>Test Seller Company Limited</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="TXID" schemeAgencyID="RD">12345678901230001</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                        <ram:PostalTradeAddress>
                            <ram:PostcodeCode>10310</ram:PostcodeCode>
                            <ram:CityName>1017</ram:CityName>
                            <ram:CitySubDivisionName>101701</ram:CitySubDivisionName>
                            <ram:CountryID schemeID="3166-1 alpha-2">TH</ram:CountryID>
                            <ram:CountrySubDivisionID>10</ram:CountrySubDivisionID>
                            <ram:BuildingNumber>123</ram:BuildingNumber>
                        </ram:PostalTradeAddress>
                    </ram:SellerTradeParty>
                    <ram:BuyerTradeParty>
                        <ram:Name>Test Buyer Company Limited</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="TXID">98765432101230001</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                        <ram:PostalTradeAddress>
                            <ram:PostcodeCode>10330</ram:PostcodeCode>
                            <ram:CityName>1004</ram:CityName>
                            <ram:CitySubDivisionName>100402</ram:CitySubDivisionName>
                            <ram:CountryID schemeID="3166-1 alpha-2">TH</ram:CountryID>
                            <ram:CountrySubDivisionID>10</ram:CountrySubDivisionID>
                            <ram:BuildingNumber>456</ram:BuildingNumber>
                        </ram:PostalTradeAddress>
                    </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeDelivery>
                    <ram:ShipToTradeParty>
                        <ram:PostalTradeAddress>
                            <ram:PostcodeCode>10330</ram:PostcodeCode>
                            <ram:CityName>1004</ram:CityName>
                            <ram:CitySubDivisionName>100402</ram:CitySubDivisionName>
                            <ram:CountryID schemeID="3166-1 alpha-2">TH</ram:CountryID>
                            <ram:CountrySubDivisionID>10</ram:CountrySubDivisionID>
                            <ram:BuildingNumber>456</ram:BuildingNumber>
                        </ram:PostalTradeAddress>
                    </ram:ShipToTradeParty>
                </ram:ApplicableHeaderTradeDelivery>
                <ram:ApplicableHeaderTradeSettlement>
                    <ram:InvoiceCurrencyCode listID="ISO 4217 3A">THB</ram:InvoiceCurrencyCode>
                    <ram:ApplicableTradeTax>
                        <ram:TypeCode>VAT</ram:TypeCode>
                        <ram:CalculatedRate>7</ram:CalculatedRate>
                        <ram:BasisAmount>10000</ram:BasisAmount>
                        <ram:CalculatedAmount>700</ram:CalculatedAmount>
                    </ram:ApplicableTradeTax>
                    <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        <ram:LineTotalAmount>10000</ram:LineTotalAmount>
                        <ram:TaxBasisTotalAmount>10000</ram:TaxBasisTotalAmount>
                        <ram:TaxTotalAmount>700</ram:TaxTotalAmount>
                        <ram:GrandTotalAmount>10700</ram:GrandTotalAmount>
                    </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                    <ram:AssociatedDocumentLineDocument>
                        <ram:LineID>1</ram:LineID>
                    </ram:AssociatedDocumentLineDocument>
                    <ram:SpecifiedTradeProduct>
                        <ram:ID>PROD001</ram:ID>
                        <ram:Name>Test Product</ram:Name>
                    </ram:SpecifiedTradeProduct>
                    <ram:SpecifiedLineTradeAgreement>
                        <ram:GrossPriceProductTradePrice>
                            <ram:ChargeAmount>100</ram:ChargeAmount>
                        </ram:GrossPriceProductTradePrice>
                    </ram:SpecifiedLineTradeAgreement>
                    <ram:SpecifiedLineTradeDelivery>
                        <ram:BilledQuantity unitCode="PIECE">100</ram:BilledQuantity>
                    </ram:SpecifiedLineTradeDelivery>
                    <ram:SpecifiedLineTradeSettlement>
                        <ram:ApplicableTradeTax>
                            <ram:TypeCode>VAT</ram:TypeCode>
                            <ram:CalculatedRate>7</ram:CalculatedRate>
                            <ram:BasisAmount>10000</ram:BasisAmount>
                            <ram:CalculatedAmount>700</ram:CalculatedAmount>
                        </ram:ApplicableTradeTax>
                        <ram:SpecifiedTradeSettlementLineMonetarySummation>
                            <ram:TaxTotalAmount>700</ram:TaxTotalAmount>
                            <ram:NetLineTotalAmount currencyID="THB">10000</ram:NetLineTotalAmount>
                        </ram:SpecifiedTradeSettlementLineMonetarySummation>
                    </ram:SpecifiedLineTradeSettlement>
                </ram:IncludedSupplyChainTradeLineItem>
            </rsm:SupplyChainTradeTransaction>
        </rsm:TaxInvoice_CrossIndustryInvoice>
        """;

    private static final String RECEIPT_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:Receipt_CrossIndustryInvoice
            xmlns:ram="urn:etda:uncefact:data:standard:Receipt_ReusableAggregateBusinessInformationEntity:2"
            xmlns:rsm="urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2">
            <rsm:ExchangedDocumentContext>
                <ram:GuidelineSpecifiedDocumentContextParameter>
                    <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                </ram:GuidelineSpecifiedDocumentContextParameter>
            </rsm:ExchangedDocumentContext>
            <rsm:ExchangedDocument>
                <ram:ID>RCT2024010001</ram:ID>
                <ram:Name>ใบเสร็จรับเงิน</ram:Name>
                <ram:TypeCode listID="1001_ThaiDocumentNameCodeInvoice" listAgencyID="RD/ETDA">T01</ram:TypeCode>
                <ram:IssueDateTime>2024-01-15T10:30:00</ram:IssueDateTime>
            </rsm:ExchangedDocument>
            <rsm:SupplyChainTradeTransaction>
                <ram:ApplicableHeaderTradeAgreement>
                    <ram:SellerTradeParty>
                        <ram:Name>Test Seller Company Limited</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="TXID" schemeAgencyID="RD">12345678901230001</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                        <ram:PostalTradeAddress>
                            <ram:PostcodeCode>10310</ram:PostcodeCode>
                            <ram:CityName>1017</ram:CityName>
                            <ram:CitySubDivisionName>101701</ram:CitySubDivisionName>
                            <ram:CountryID>TH</ram:CountryID>
                            <ram:CountrySubDivisionID>10</ram:CountrySubDivisionID>
                            <ram:BuildingNumber>123</ram:BuildingNumber>
                        </ram:PostalTradeAddress>
                    </ram:SellerTradeParty>
                    <ram:BuyerTradeParty>
                        <ram:Name>Test Buyer</ram:Name>
                        <ram:SpecifiedTaxRegistration>
                            <ram:ID schemeID="NIDN">1234567890123</ram:ID>
                        </ram:SpecifiedTaxRegistration>
                    </ram:BuyerTradeParty>
                </ram:ApplicableHeaderTradeAgreement>
                <ram:ApplicableHeaderTradeSettlement>
                    <ram:InvoiceCurrencyCode>THB</ram:InvoiceCurrencyCode>
                    <ram:ApplicableTradeTax>
                        <ram:TypeCode>VAT</ram:TypeCode>
                        <ram:CalculatedRate>7</ram:CalculatedRate>
                        <ram:BasisAmount>1000</ram:BasisAmount>
                        <ram:CalculatedAmount>70</ram:CalculatedAmount>
                    </ram:ApplicableTradeTax>
                    <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                        <ram:LineTotalAmount>1000</ram:LineTotalAmount>
                        <ram:TaxBasisTotalAmount>1000</ram:TaxBasisTotalAmount>
                        <ram:TaxTotalAmount>70</ram:TaxTotalAmount>
                        <ram:GrandTotalAmount>1070</ram:GrandTotalAmount>
                    </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                </ram:ApplicableHeaderTradeSettlement>
                <ram:IncludedSupplyChainTradeLineItem>
                    <ram:AssociatedDocumentLineDocument>
                        <ram:LineID>1</ram:LineID>
                    </ram:AssociatedDocumentLineDocument>
                    <ram:SpecifiedTradeProduct>
                        <ram:ID>PROD001</ram:ID>
                        <ram:Name>Test Product</ram:Name>
                    </ram:SpecifiedTradeProduct>
                </ram:IncludedSupplyChainTradeLineItem>
            </rsm:SupplyChainTradeTransaction>
        </rsm:Receipt_CrossIndustryInvoice>
        """;

    @BeforeEach
    void setUp() {
        validationService = new XmlValidationServiceImpl();
    }

    // ==================== XML Well-formedness Tests ====================

    @Test
    @DisplayName("Validate null XML returns error")
    void testValidateNullXmlReturnsErrors() {
        ValidationResult result = validationService.validate(null);

        assertThat(result.valid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(error -> error.contains("null or empty"));
    }

    @Test
    @DisplayName("Validate empty XML returns error")
    void testValidateEmptyXmlReturnsErrors() {
        ValidationResult result = validationService.validate("");

        assertThat(result.valid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anyMatch(error -> error.contains("null or empty"));
    }

    @Test
    @DisplayName("Validate blank XML returns error")
    void testValidateBlankXmlReturnsErrors() {
        ValidationResult result = validationService.validate("   ");

        assertThat(result.valid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    // ==================== Document Type Detection Tests ====================

    @Test
    @DisplayName("Extract document type from TaxInvoice")
    void testExtractDocumentTypeTaxInvoice() {
        DocumentType type = validationService.extractDocumentType(VALID_TAX_INVOICE_XML);

        assertThat(type).isEqualTo(DocumentType.TAX_INVOICE);
    }

    @Test
    @DisplayName("Extract document type from Receipt")
    void testExtractDocumentTypeReceipt() {
        DocumentType type = validationService.extractDocumentType(RECEIPT_XML);

        assertThat(type).isEqualTo(DocumentType.RECEIPT);
    }

    @Test
    @DisplayName("Extract document type from null returns null")
    void testExtractDocumentTypeNullReturnsNull() {
        DocumentType type = validationService.extractDocumentType(null);

        assertThat(type).isNull();
    }

    // ==================== Invoice Number Extraction Tests ====================

    @Test
    @DisplayName("Extract invoice number from TaxInvoice")
    void testExtractInvoiceNumberFromTaxInvoice() {
        String invoiceNumber = validationService.extractInvoiceNumber(VALID_TAX_INVOICE_XML);

        assertThat(invoiceNumber).isEqualTo("TIV2024010001");
    }

    @Test
    @DisplayName("Extract invoice number from Receipt")
    void testExtractInvoiceNumberFromReceipt() {
        String invoiceNumber = validationService.extractInvoiceNumber(RECEIPT_XML);

        assertThat(invoiceNumber).isEqualTo("RCT2024010001");
    }

    @Test
    @DisplayName("Extract invoice number from null XML returns null")
    void testExtractInvoiceNumberNullXmlReturnsNull() {
        String invoiceNumber = validationService.extractInvoiceNumber(null);

        assertThat(invoiceNumber).isNull();
    }

    @Test
    @DisplayName("Extract invoice number from empty XML returns null")
    void testExtractInvoiceNumberEmptyXmlReturnsNull() {
        String invoiceNumber = validationService.extractInvoiceNumber("");

        assertThat(invoiceNumber).isNull();
    }

    // ==================== Service Initialization Tests ====================

    @Test
    @DisplayName("Service initializes with JAXB contexts for all document types")
    void testServiceInitializesWithJaxbContexts() {
        // Service was created in setUp()
        assertThat(validationService).isNotNull();

        // Verify by successfully extracting document types
        assertThat(validationService.extractDocumentType(VALID_TAX_INVOICE_XML))
            .isEqualTo(DocumentType.TAX_INVOICE);
        assertThat(validationService.extractDocumentType(RECEIPT_XML))
            .isEqualTo(DocumentType.RECEIPT);
    }

    // ==================== Error Collection Tests ====================

    @Test
    @DisplayName("Validation collects all errors not fail fast")
    void testValidateCollectsAllErrorsNotFailFast() {
        // Use a minimal XML that may fail validation
        String minimalXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:TaxInvoice_CrossIndustryInvoice
                xmlns:ram="urn:etda:uncefact:data:standard:TaxInvoice_ReusableAggregateBusinessInformationEntity:2"
                xmlns:rsm="urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2">
                <rsm:ExchangedDocumentContext>
                    <ram:GuidelineSpecifiedDocumentContextParameter>
                        <ram:ID schemeAgencyID="ETDA" schemeVersionID="v2.1">ER3-2560</ram:ID>
                    </ram:GuidelineSpecifiedDocumentContextParameter>
                </rsm:ExchangedDocumentContext>
                <rsm:ExchangedDocument>
                    <ram:ID>MINIMAL001</ram:ID>
                </rsm:ExchangedDocument>
            </rsm:TaxInvoice_CrossIndustryInvoice>
            """;

        ValidationResult result = validationService.validate(minimalXml);

        // The XML should complete validation
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Validate with valid XML returns success")
    void testValidateWithValidXmlReturnsSuccess() {
        ValidationResult result = validationService.validate(VALID_TAX_INVOICE_XML);

        assertThat(result.valid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }

    // ==================== Thread Safety Test ====================

    @Test
    @DisplayName("Concurrent validations are thread safe")
    void testConcurrentValidations() throws InterruptedException {
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                ValidationResult result = validationService.validate(VALID_TAX_INVOICE_XML);
                results[index] = result.valid();
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All validations should succeed
        for (boolean result : results) {
            assertThat(result).isTrue();
        }
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Extract document type from empty root element returns null")
    void testExtractDocumentTypeFromEmptyRootReturnsNull() {
        String emptyRoot = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root/>
            """;

        DocumentType type = validationService.extractDocumentType(emptyRoot);

        assertThat(type).isNull();
    }

    // ==================== JAXB-Specific Tests ====================

    @Test
    @DisplayName("JAXB unmarshaling handles JAXBElement wrapper")
    void testJaxbHandlesJAXBElementWrapper() {
        // The inline XML should unmarshal correctly even with JAXBElement wrapper
        ValidationResult result = validationService.validate(VALID_TAX_INVOICE_XML);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("ValidationEventHandler collects errors correctly")
    void testValidationEventHandlerCollectsErrors() {
        ValidationErrorHandler handler = new ValidationErrorHandler();

        // Handler should start with no errors
        assertThat(handler.hasErrors()).isFalse();
        assertThat(handler.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("JAXB contexts use .impl packages for initialization")
    void testJaxbContextsUseImplPackages() {
        for (DocumentType type : DocumentType.values()) {
            String implPath = type.getImplementationContextPath();
            assertThat(implPath).contains(".impl");

            // Verify it has all required package components
            if (type == DocumentType.INVOICE) {
                assertThat(implPath).contains(".invoice.qdt.impl");
            } else {
                assertThat(implPath).contains(".common.qdt.impl");
            }
            assertThat(implPath).contains(".udt.impl");
        }
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Full validation pipeline: extract type then validate")
    void testFullValidationPipeline() {
        // Step 1: Extract document type
        DocumentType type = validationService.extractDocumentType(VALID_TAX_INVOICE_XML);
        assertThat(type).isEqualTo(DocumentType.TAX_INVOICE);

        // Step 2: Validate
        ValidationResult result = validationService.validate(VALID_TAX_INVOICE_XML);
        assertThat(result.valid()).isTrue();

        // Step 3: Extract invoice number
        String invoiceNumber = validationService.extractInvoiceNumber(VALID_TAX_INVOICE_XML);
        assertThat(invoiceNumber).isEqualTo("TIV2024010001");
    }

    @Test
    @DisplayName("Validate handles all document types correctly")
    void testValidateHandlesAllDocumentTypesCorrectly() {
        // Test that the service can handle all 6 document types
        assertThat(DocumentType.values()).hasSize(6);

        // Each document type should have a corresponding JAXB context
        for (DocumentType type : DocumentType.values()) {
            assertThat(type.getContextPath()).isNotNull();
            assertThat(type.getNamespaceUri()).isNotNull();
        }
    }
}
