package com.muditsahni.documentstore.model.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.muditsahni.documentstore.model.entity.document.type.BankDetail
import com.muditsahni.documentstore.model.entity.document.type.BilledAmount
import com.muditsahni.documentstore.model.entity.document.type.Customer
import com.muditsahni.documentstore.model.entity.document.type.Discount
import com.muditsahni.documentstore.model.entity.document.type.Invoice
import com.muditsahni.documentstore.model.entity.document.type.InvoiceWrapper
import com.muditsahni.documentstore.model.entity.document.type.LineItem
import com.muditsahni.documentstore.model.entity.document.type.Quantity
import com.muditsahni.documentstore.model.entity.document.type.Tax
import com.muditsahni.documentstore.model.entity.document.type.TaxCategory
import com.muditsahni.documentstore.model.entity.document.type.Vendor


data class InvoiceWrapperDTO(
    val invoice: InvoiceDTO
)

fun InvoiceWrapperDTO.toInvoiceWrapper(): InvoiceWrapper {
    return InvoiceWrapper(
        invoice = invoice.toInvoice()
    )
}

data class InvoiceDTO(
    @JsonProperty("invoice_number")
    val invoiceNumber: String? = null,
    @JsonProperty("billing_date")
    val billingDate: String? = null,
    @JsonProperty("due_date")
    val dueDate: String? = null,
    @JsonProperty("place_of_supply")
    val placeOfSupply: String? = null,
    @JsonProperty("irn_number")
    val irnNumber: String? = null,
    @JsonProperty("currency_code")
    val currencyCode: String? = null,
    val customer: CustomerDTO? = null,
    val vendor: VendorDTO? = null,
    @JsonProperty("billed_amount")
    val billedAmount: BilledAmountDTO? = null,
    @JsonProperty("line_items")
    val lineItems: List<LineItemDTO> = emptyList()
)

fun InvoiceDTO.toInvoice(): Invoice {
    return Invoice(
        invoiceNumber = invoiceNumber,
        billingDate = billingDate,
        dueDate = dueDate,
        placeOfSupply = placeOfSupply,
        irnNumber = irnNumber,
        currencyCode = currencyCode,
        customer = customer?.toCustomer(),
        vendor = vendor?.toVendor(),
        billedAmount = billedAmount?.toBilledAmount(),
        lineItems = lineItems.map { it.toLineItem() }
    )
}

data class CustomerDTO(
    val name: String? = null,
    @JsonProperty("billing_address")
    val billingAddress: String? = null,
    @JsonProperty("shipping_address")
    val shippingAddress: String? = null,
    @JsonProperty("gst_number")
    val gstNumber: String? = null,
    val pan: String? = null
)

fun CustomerDTO.toCustomer(): Customer {
    return Customer(
        name = name,
        billingAddress = billingAddress,
        shippingAddress = shippingAddress,
        gstNumber = gstNumber,
        pan = pan
    )
}


data class VendorDTO(
    val name: String? = null,
    val address: String? = null,
    @JsonProperty("gst_number")
    val gstNumber: String? = null,
    @JsonProperty("bank_details")
    val bankDetails: List<BankDetailDTO>? = null,
    val pan: String? = null,
    @JsonProperty("upi_id")
    val upiId: String? = null
)

fun VendorDTO.toVendor(): Vendor {
    return Vendor(
        name = name,
        address = address,
        gstNumber = gstNumber,
        bankDetails = bankDetails?.map { it.toBankDetail() },
        pan = pan,
        upiId = upiId
    )
}

data class BankDetailDTO(
    @JsonProperty("bank_name")
    val bankName: String? = null,
    @JsonProperty("account_number")
    val accountNumber: String? = null,
    @JsonProperty("branch_address")
    val branchAddress: String? = null,
    val ifsc: String? = null,
    val branch: String? = null
)

fun BankDetailDTO.toBankDetail(): BankDetail {
    return BankDetail(
        bankName = bankName,
        accountNumber = accountNumber,
        branchAddress = branchAddress,
        ifsc = ifsc,
        branch = branch
    )
}

data class BilledAmountDTO(
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

fun BilledAmountDTO.toBilledAmount(): BilledAmount {
    return BilledAmount(
        subTotal = subTotal,
        total = total,
        balanceDue = balanceDue,
        amountInWords = amountInWords,
        previousDues = previousDues
    )
}

data class LineItemDTO(
    val description: String? = null,
    @JsonProperty("hsn_sac")
    val hsnSac: String? = null,
    val quantity: QuantityDTO? = null,
    val rate: Double? = null,
    val amount: Double? = null,
    val discount: DiscountDTO? = null,
    val taxes: List<TaxDTO> = emptyList()
)

fun LineItemDTO.toLineItem(): LineItem {
    return LineItem(
        description = description,
        hsnSac = hsnSac,
        quantity = quantity?.toQuantity(),
        rate = rate,
        amount = amount,
        discount = discount?.toDiscount(),
        taxes = taxes.map { it.toTax() }
    )
}

data class DiscountDTO(
    val percentage: Double? = null,
    val amount: Double? = null
)

fun DiscountDTO.toDiscount(): Discount {
    return Discount(
        percentage = percentage,
        amount = amount
    )
}
data class QuantityDTO(
    val value: Double? = null,
    val unit: String? = null
)

fun QuantityDTO.toQuantity(): Quantity {
    return Quantity(
        value = value,
        unit = unit
    )
}


data class TaxDTO(
    val category: TaxCategory? = null,
    val rate: Int? = null,
    val amount: Double? = null
)

fun TaxDTO.toTax(): Tax {
    return Tax(
        category = category,
        rate = rate,
        amount = amount
    )
}