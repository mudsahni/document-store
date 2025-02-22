package com.muditsahni.documentstore.model.entity.document.type

enum class RequiredInvoiceFields(val field: String) {
    INVOICE_NUMBER("invoiceNumber"),
    INVOICE_DATE("invoiceDate"),
    INVOICE_TOTAL("invoiceTotal"),
    INVOICE_CURRENCY("invoiceCurrency"),
    INVOICE_DUE_DATE("invoiceDueDate"),
    INVOICE_LINE_ITEMS("invoiceLineItems")
}