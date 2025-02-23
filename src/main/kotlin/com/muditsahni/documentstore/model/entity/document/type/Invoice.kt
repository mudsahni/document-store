package com.muditsahni.documentstore.model.entity.document.type

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class InvoiceWrapper(
    val invoice: Invoice
)

data class Invoice(
    val invoiceNumber: String? = null,
    val billingDate: String? = null,
    val dueDate: String? = null,
    val placeOfSupply: String? = null,
    val currencyCode: String? = null,
    val customer: Customer? = null,
    val vendor: Vendor? = null,
    val billedAmount: BilledAmount? = null,
    val lineItems: List<LineItem> = emptyList()
)

data class Customer(
    val name: String? = null,
    val billingAddress: String? = null,
    val shippingAddress: String? = null,
    val gstNumber: String? = null,
    val pan: String? = null
)

data class Vendor(
    val name: String? = null,
    val address: String? = null,
    val gstNumber: String? = null,
    val bankDetails: List<BankDetail>? = null,
    val pan: String? = null,
    val upiId: String? = null
)

data class BankDetail(
    val bankName: String? = null,
    val accountNumber: String? = null,
    val branchAddress: String? = null,
    val ifsc: String? = null,
    val branch: String? = null
)

data class BilledAmount(
    val subTotal: Double? = null,
    val total: Double? = null,
    val balanceDue: Double? = null,
    val amountInWords: String? = null,
    val previousDues: Double? = null
)

data class LineItem(
    val description: String? = null,
    val hsnSac: String? = null,
    val quantity: Quantity? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val discount: Discount? = null,
    val taxes: List<Tax> = emptyList()
)

data class Discount(
    val percentage: Double? = null,
    val amount: Double? = null
)

data class Quantity(
    val value: Double? = null,
    val unit: String? = null
)


@JsonDeserialize(using = TaxCategoryDeserializer::class)
enum class TaxCategory(val value: String) {
    CGST("CGST"),
    SGST("SGST"),
    IGST("IGST"),
    CESS("CESS"),
    GST("GST"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String): TaxCategory? {
            // First try exact match
            TaxCategory.entries.find { it.value == value }?.let { return it }

            // If no exact match, try to find as substring
            val upperValue = value.uppercase()
            return TaxCategory.entries.find { upperValue.contains(it.value) }
        }
    }

}

class TaxCategoryDeserializer : JsonDeserializer<TaxCategory>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TaxCategory? {
        val value = p.valueAsString
        return TaxCategory.fromString(value) ?: return TaxCategory.UNKNOWN
        // Or return null instead of throwing if you want to handle missing matches gracefully
        // return TaxCategory.fromString(value)
    }
}


data class Tax(
    val category: TaxCategory? = null,
    val rate: Int? = null,
    val amount: Double? = null
)
