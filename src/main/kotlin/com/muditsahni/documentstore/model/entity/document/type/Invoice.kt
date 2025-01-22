package com.muditsahni.documentstore.model.entity.document.type

import com.fasterxml.jackson.annotation.JsonProperty

data class InvoiceWrapper(
    val invoice: Invoice
)

data class Invoice(
    @JsonProperty("invoice_number")
    val invoiceNumber: String? = null,
    @JsonProperty("billing_date")
    val billingDate: String? = null,
    @JsonProperty("due_date")
    val dueDate: String? = null,
    @JsonProperty("place_of_supply")
    val placeOfSupply: String? = null,
    @JsonProperty("currency_code")
    val currencyCode: String? = null,
    val customer: Customer? = null,
    val vendor: Vendor? = null,
    @JsonProperty("billed_amount")
    val billedAmount: BilledAmount? = null,
    @JsonProperty("line_items")
    val lineItems: List<LineItem> = emptyList()
)

data class Customer(
    val name: String? = null,
    @JsonProperty("billing_address")
    val billingAddress: String? = null,
    @JsonProperty("shipping_address")
    val shippingAddress: String? = null,
    @JsonProperty("gst_number")
    val gstNumber: String? = null,
    val pan: String? = null
)

data class Vendor(
    val name: String? = null,
    val address: String? = null,
    @JsonProperty("gst_number")
    val gstNumber: String? = null,
    @JsonProperty("bank_details")
    val bankDetails: List<BankDetail> = emptyList(),
    val pan: String? = null,
    @JsonProperty("upi_id")
    val upiId: String? = null
)

data class BankDetail(
    @JsonProperty("bank_name")
    val bankName: String? = null,
    @JsonProperty("account_number")
    val accountNumber: String? = null,
    @JsonProperty("branch_address")
    val branchAddress: String? = null,
    val ifsc: String? = null,
    val branch: String? = null
)

data class BilledAmount(
    @JsonProperty("sub_total")
    val subTotal: Double? = null,
    val total: Double? = null,
    @JsonProperty("balance_due")
    val balanceDue: Double? = null,
    @JsonProperty("amount_in_words")
    val amountInWords: String? = null,
    @JsonProperty("previous_dues")
    val previousDues: Double? = null
)

data class LineItem(
    val description: String? = null,
    @JsonProperty("hsn_sac")
    val hsnSac: String? = null,
    val quantity: Quantity? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val discount: Double? = null,
    val taxes: List<Tax> = emptyList()
)

data class Quantity(
    val value: Double? = null,
    val unit: String? = null
)


enum class TaxCategory(val value: String) {
    CGST("CGST"),
    SGST("SGST"),
    IGST("IGST"),
    CESS("CESS")
}

data class Tax(
    val category: TaxCategory? = null,
    val rate: Int? = null,
    val amount: Double? = null
)
