package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.model.DeclarationResult;
import com.erp.integrationhub.domain.model.InvoiceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying InvoiceStatus parsing and provider response mapping.
 */
class InvoiceStatusTest {

    @Test
    @DisplayName("An observed status translates to OBSERVED, not rejection")
    void anObservedStatusDoesNotBecomeRejection() {
        assertEquals(InvoiceStatus.OBSERVED, InvoiceStatus.fromProvider("OBSERVADO"));
        assertEquals(InvoiceStatus.OBSERVED, InvoiceStatus.fromProvider("observado"));
        assertEquals(InvoiceStatus.OBSERVED, InvoiceStatus.fromProvider("CON OBSERVACIONES"));

        assertTrue(InvoiceStatus.OBSERVED.isIssued(),
                "An observed invoice IS issued: cannot be re-issued");
        assertFalse(InvoiceStatus.OBSERVED.isRetryable(),
                "An observed invoice cannot be retried: it already exists");
    }

    @Test
    @DisplayName("An unknown status does not become a rejection")
    void anUnknownStatusDoesNotBecomeRejection() {
        assertEquals(InvoiceStatus.UNKNOWN, InvoiceStatus.fromProvider("ESTADO_NUEVO"));
        assertEquals(InvoiceStatus.UNKNOWN, InvoiceStatus.fromProvider(null));
        assertEquals(InvoiceStatus.UNKNOWN, InvoiceStatus.fromProvider("  "));
    }

    @Test
    @DisplayName("Response code matches what provider reported")
    void responseCodeMatchesProvider() {
        DeclarationResult result = DeclarationResult.withStatus(
                InvoiceStatus.REJECTED, "2335", "Invoice was observed", null, null, null, null);

        assertEquals("2335", result.sunatResponseCode(),
                "Provider code cannot be replaced by a literal");
        assertEquals(InvoiceStatus.REJECTED, result.status());
    }

    @Test
    @DisplayName("An observed status preserves code and message")
    void observedStatusPreservesCodeAndMessage() {
        DeclarationResult result = DeclarationResult.observed(
                "0", "Accepted with observation", "pdf", "xml", "cdr", "hash");

        assertEquals(InvoiceStatus.OBSERVED, result.status());
        assertEquals("0", result.sunatResponseCode());
        assertTrue(result.successful(), "An observed voucher has been accepted");
    }
}
