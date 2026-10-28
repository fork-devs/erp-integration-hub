package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain entity representing an electronic invoice or voucher prepared for declaration to SUNAT.
 */
public class InvoiceToDeclare implements Serializable {
    private final UUID tenantId;
    private final UUID invoiceId;
    private final InvoiceType type;
    private final String series;
    private final int number;
    private final String customerDocType;
    private final String customerDocNumber;
    private final String customerLegalName;
    private final String customerAddress;
    private final String customerEmail;
    private final Currency currency;
    private final BigDecimal totalTaxable;
    private final BigDecimal totalTax;
    private final BigDecimal total;
    private final List<InvoiceItem> items;
    private final LocalDate issueDate;

    public InvoiceToDeclare(
            UUID tenantId,
            UUID invoiceId,
            InvoiceType type,
            String series,
            int number,
            String customerDocType,
            String customerDocNumber,
            String customerLegalName,
            String customerAddress,
            String customerEmail,
            Currency currency,
            BigDecimal totalTaxable,
            BigDecimal totalTax,
            BigDecimal total,
            List<InvoiceItem> items,
            LocalDate issueDate) {
        if (tenantId == null || invoiceId == null || type == null || series == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Required invoice fields cannot be null or empty");
        }
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.type = type;
        this.series = series.toUpperCase().trim();
        this.number = number;
        this.customerDocType = customerDocType != null ? customerDocType : (type == InvoiceType.FACTURA ? "6" : "1");
        this.customerDocNumber = customerDocNumber != null ? customerDocNumber.trim() : "";
        this.customerLegalName = customerLegalName != null ? customerLegalName.trim() : "CLIENTES VARIOS";
        this.customerAddress = customerAddress != null ? customerAddress.trim() : "";
        this.customerEmail = customerEmail != null ? customerEmail.trim() : "";
        this.currency = currency != null ? currency : Currency.PEN;
        this.totalTaxable = totalTaxable != null ? totalTaxable : BigDecimal.ZERO;
        this.totalTax = totalTax != null ? totalTax : BigDecimal.ZERO;
        this.total = total != null ? total : BigDecimal.ZERO;
        this.items = List.copyOf(items);
        this.issueDate = issueDate != null ? issueDate : LocalDate.now();
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public InvoiceType getType() {
        return type;
    }

    public String getSeries() {
        return series;
    }

    public int getNumber() {
        return number;
    }

    public String getCustomerDocType() {
        return customerDocType;
    }

    public String getCustomerDocNumber() {
        return customerDocNumber;
    }

    public String getCustomerLegalName() {
        return customerLegalName;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getTotalTaxable() {
        return totalTaxable;
    }

    public BigDecimal getTotalTax() {
        return totalTax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    // --- Backward compatibility accessors ---

    public UUID getComprobanteId() {
        return invoiceId;
    }

    public InvoiceType getTipo() {
        return type;
    }

    public String getSerie() {
        return series;
    }

    public int getNumero() {
        return number;
    }

    public String getClienteTipoDoc() {
        return customerDocType;
    }

    public String getClienteNumDoc() {
        return customerDocNumber;
    }

    public String getClienteDenominacion() {
        return customerLegalName;
    }

    public String getClienteDireccion() {
        return customerAddress;
    }

    public String getClienteEmail() {
        return customerEmail;
    }

    public Currency getMoneda() {
        return currency;
    }

    public BigDecimal getTotalGravada() {
        return totalTaxable;
    }

    public BigDecimal getTotalIgv() {
        return totalTax;
    }

    public LocalDate getFechaEmision() {
        return issueDate;
    }
}
