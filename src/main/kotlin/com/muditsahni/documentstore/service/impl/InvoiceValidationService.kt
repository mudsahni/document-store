package com.muditsahni.documentstore.service.impl

import com.muditsahni.documentstore.exception.ValidationError
import com.muditsahni.documentstore.model.entity.document.type.BankDetail
import com.muditsahni.documentstore.model.entity.document.type.BilledAmount
import com.muditsahni.documentstore.model.entity.document.type.Customer
import com.muditsahni.documentstore.model.entity.document.type.Discount
import com.muditsahni.documentstore.model.entity.document.type.Invoice
import com.muditsahni.documentstore.model.entity.document.type.InvoiceWrapper
import com.muditsahni.documentstore.model.entity.document.type.LineItem
import com.muditsahni.documentstore.model.entity.document.type.Quantity
import com.muditsahni.documentstore.model.entity.document.type.Tax
import com.muditsahni.documentstore.model.entity.document.type.Vendor
import mu.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs


private const val FLOAT_TOLERANCE = 0.01
private val dateRegex = Regex("\\d{2}-\\d{2}-\\d{4}")

//private val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
private val currencyRegex = Regex("^[A-Z]{3}\$")
private val gstRegex = Regex("^[0-9A-Z]{15}\$")
private val panRegex = Regex("^[A-Z]{5}[0-9]{4}[A-Z]\$")
private val ifscRegex = Regex("^[A-Z]{4}0[A-Z0-9]{6}\$")
private val upiIdRegex = Regex("^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+\$")

val logger = KotlinLogging.logger { "Validation" }
fun isValidDate(dateStr: String, formats: List<String> = listOf("dd\\MM\\yyyy", "yyyy-MM-dd", "dd-MMM-yy")): Boolean {
    return formats.any { format ->
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format))
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}

fun parseDate(dateStr: String): LocalDate? {
    val formats = listOf(
        "dd-MMM-yy",    // 10-Sep-24
        "dd-MMM-yyyy",   // 10-Sep-2024
        "dd\\MM\\yyyy"   // 10\09\2024
    )

    formats.forEach { format ->
        try {
            val formatter = DateTimeFormatter.ofPattern(format)
            return LocalDate.parse(dateStr, formatter)
        } catch (e: DateTimeParseException) {
            // Try next format
        }
    }
    return null
}

fun validateInvoiceWrapper(wrapper: InvoiceWrapper): Map<String, ValidationError> {
    // Prefix all errors from the invoice with "invoice."
    return validateInvoice(wrapper.invoice).map { error ->
        error.copy(field = "invoice.${error.field}")
    }.associateBy { it.field }
}

fun validateInvoice(invoice: Invoice): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()

    // Invoice Number
    if (invoice.invoiceNumber.isNullOrBlank()) {
        errors.add(ValidationError("invoiceNumber", "Invoice number is required."))
    }

    // Billing Date
    var billingDate = LocalDate.MIN

    if (invoice.billingDate.isNullOrBlank() || isValidDate(invoice.billingDate)) {
        errors.add(ValidationError("billingDate", "Billing date is null or of an unexpected format. ${invoice.billingDate}"))
    } else {
        try {
            billingDate = parseDate(invoice.billingDate)
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("billingDate", "Billing date is invalid."))
        }
    }

    // Due Date
    var dueDate = LocalDate.MIN

    if (invoice.dueDate.isNullOrBlank() || isValidDate(invoice.dueDate)) {
        errors.add(ValidationError("dueDate", "Due date is null or of an unexpected format. ${invoice.dueDate}"))
    } else {
        try {
            dueDate = parseDate(invoice.dueDate)
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("dueDate", "Due date is invalid."))
        }
    }


    if (dueDate != LocalDate.MIN && billingDate != LocalDate.MIN && !invoice.dueDate.isNullOrBlank() && !invoice.billingDate.isNullOrBlank()) {
        try {
            if (!dueDate.isAfter(billingDate)) {
                errors.add(ValidationError("dueDate", "Due date must be after billing date."))
            }
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("billingDate/dueDate", "Billing date or due date is invalid."))
        }
    }

    // Place of Supply
    if (invoice.placeOfSupply.isNullOrBlank()) {
        errors.add(ValidationError("placeOfSupply", "Place of supply is required."))
    }

    // Currency Code
    if (invoice.currencyCode.isNullOrBlank() || !currencyRegex.matches(invoice.currencyCode)) {
        errors.add(ValidationError("currencyCode", "Currency code must be a valid ISO code (e.g., USD)."))
    }

    // Customer
    if (invoice.customer == null) {
        errors.add(ValidationError("customer", "Customer is required."))
    } else {
        errors.addAll(
            validateCustomer(invoice.customer)
                .map { it.copy(field = "customer.${it.field}") }
        )
    }

    // Vendor
    if (invoice.vendor == null) {
        errors.add(ValidationError("vendor", "Vendor is required."))
    } else {
        errors.addAll(
            validateVendor(invoice.vendor)
                .map { it.copy(field = "vendor.${it.field}") }
        )
    }

    // Billed Amount
    if (invoice.billedAmount == null) {
        errors.add(ValidationError("billedAmount", "Billed amount is required."))
    } else {
        errors.addAll(
            validateBilledAmount(invoice.billedAmount)
                .map { it.copy(field = "billedAmount.${it.field}") }
        )
    }

    // Line Items
    if (invoice.lineItems.isEmpty()) {
        errors.add(ValidationError("lineItems", "At least one line item is required."))
    } else {
        var totalLineItemsAmount = 0.0
        invoice.lineItems.forEachIndexed { index, lineItem ->
            val liErrors = validateLineItem(lineItem, index)
            liErrors.forEach { error ->
                errors.add(error.copy(field = "lineItems[$index].${error.field}"))
            }
            totalLineItemsAmount += (lineItem.amount ?: 0.0)
        }
        val billedTotal = invoice.billedAmount?.total ?: 0.0
        if (abs(totalLineItemsAmount - billedTotal) > FLOAT_TOLERANCE) {
            errors.add(ValidationError("billedAmount.total", "Sum of line item amounts ($totalLineItemsAmount) does not match billed total ($billedTotal)."))
        }
    }

    return errors
}

fun validateCustomer(customer: Customer): List<ValidationError> {

    val errors = mutableListOf<ValidationError>()
    if (customer.name.isNullOrBlank()) {
        errors.add(ValidationError("name", "Customer name is required."))
    }
    if (customer.billingAddress.isNullOrBlank()) {
        errors.add(ValidationError("billingAddress", "Customer billing address is required."))
    }
    if (!customer.gstNumber.isNullOrBlank() && !gstRegex.matches(customer.gstNumber)) {
        errors.add(ValidationError("gstNumber", "Customer GST number is invalid."))
    }

    if (customer.pan.isNullOrBlank() || !panRegex.matches(customer.pan)) {
        errors.add(ValidationError("pan", "Customer PAN is null or invalid."))
    }

    return errors
}

fun validateVendor(vendor: Vendor): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (vendor.name.isNullOrBlank()) {
        errors.add(ValidationError("name", "Vendor name is required."))
    }
    if (vendor.address.isNullOrBlank()) {
        errors.add(ValidationError("address", "Vendor address is required."))
    }
    if (vendor.gstNumber.isNullOrBlank() || !gstRegex.matches(vendor.gstNumber)) {
        errors.add(ValidationError("gstNumber", "Vendor GST number is invalid."))
    }
    if (vendor.pan.isNullOrBlank() || !panRegex.matches(vendor.pan)) {
        errors.add(ValidationError("pan", "Vendor PAN is invalid."))
    }

    if (vendor.upiId.isNullOrBlank() || !upiIdRegex.matches(vendor.upiId)) {
        errors.add(ValidationError("pan", "Vendor UPI Id is missing or invalid."))
    }

    vendor.bankDetails?.forEachIndexed { index, bankDetail ->
        errors.addAll(
            validateBankDetail(bankDetail, index)
                .map { it.copy(field = "bankDetails[$index].${it.field}") }
        )
    }
    return errors
}

fun validateBankDetail(bankDetail: BankDetail, index: Int): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (bankDetail.bankName.isNullOrBlank()) {
        errors.add(ValidationError("bankName", "Bank name is required."))
    }
    if (bankDetail.accountNumber.isNullOrBlank()) {
        errors.add(ValidationError("accountNumber", "Account number is required."))
    }
    if (bankDetail.branchAddress.isNullOrBlank()) {
        errors.add(ValidationError("branchAddress", "Branch address is required."))
    }
    if (bankDetail.ifsc.isNullOrBlank()) {
        errors.add(ValidationError("ifsc", "IFSC code is required."))
    } else if (!ifscRegex.matches(bankDetail.ifsc)) {
        errors.add(ValidationError("ifsc", "IFSC code is invalid."))
    }
    return errors
}

fun validateBilledAmount(billedAmount: BilledAmount): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (billedAmount.subTotal == null || billedAmount.subTotal < 0) {
        errors.add(ValidationError("subTotal", "Subtotal must be non-negative."))
    }
    if (billedAmount.total == null || billedAmount.total < 0) {
        errors.add(ValidationError("total", "Total must be non-negative."))
    }
    if (billedAmount.balanceDue == null || billedAmount.balanceDue < 0) {
        errors.add(ValidationError("balanceDue", "Balance due must be non-negative."))
    }
    if (billedAmount.previousDues != null && billedAmount.previousDues < 0) {
        errors.add(ValidationError("previousDues", "Previous dues must be non-negative."))
    }
    if (billedAmount.amountInWords.isNullOrBlank()) {
        errors.add(ValidationError("amountInWords", "Amount in words is required."))
    }
    return errors
}

fun validateLineItem(item: LineItem, index: Int): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    // Use a field prefix like "description", "quantity", etc.
    if (item.description.isNullOrBlank()) {
        errors.add(ValidationError("description", "Description is required."))
    }
    if (item.quantity == null) {
        errors.add(ValidationError("quantity", "Quantity is required."))
    } else {
        errors.addAll(validateQuantity(item.quantity, index).map { it.copy(field = "quantity.${it.field}") })
    }
    if (item.rate == null || item.rate <= 0) {
        errors.add(ValidationError("rate", "Rate must be provided and greater than zero."))
    }
    if (item.amount == null) {
        errors.add(ValidationError("amount", "Amount is required."))
    } else {
        // Calculate expected amount: quantity.value * rate - discount + taxes
        val quantityValue = item.quantity?.value ?: 0.0
        val rate = item.rate ?: 0.0

        val discountAmount = if (item.discount != null) {
            if (item.discount.percentage != null) {
                quantityValue * rate * item.discount.percentage / 100.0
            } else {
                item.discount.amount ?: 0.0
            }
        } else 0.0

        val taxTotal = item.taxes.sumOf { it.amount ?: 0.0 }
        val expectedAmount = quantityValue * rate - discountAmount + taxTotal
        if (abs(expectedAmount - item.amount) > FLOAT_TOLERANCE) {
            errors.add(ValidationError("amount", "Amount (${item.amount}) does not match expected value ($expectedAmount) based on quantity, rate, discount, and taxes."))
        }

        // Validate discount consistency if provided
        if (item.discount != null) {
            errors.addAll(validateDiscount(item.discount, quantityValue * rate, index).map { it.copy(field = "discount.${it.field}") })
        }

        // Validate each tax
        item.taxes.forEachIndexed { taxIndex, tax ->
            errors.addAll(validateTax(tax, quantityValue * rate - discountAmount, index, taxIndex)
                .map { it.copy(field = "taxes[$taxIndex].${it.field}") })
        }
    }
    return errors
}

fun validateDiscount(discount: Discount, baseAmount: Double, lineItemIndex: Int): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (discount.percentage != null && discount.amount != null) {
        val expectedDiscountAmount = baseAmount * discount.percentage / 100.0
        if (abs(expectedDiscountAmount - discount.amount) > FLOAT_TOLERANCE) {
            errors.add(ValidationError("amount", "Discount amount (${discount.amount}) does not match discount percentage (${discount.percentage}) on base amount ($baseAmount). Expected: $expectedDiscountAmount"))
        }
    }
    return errors
}

fun validateQuantity(quantity: Quantity, lineItemIndex: Int): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (quantity.value == null || quantity.value <= 0) {
        errors.add(ValidationError("value", "Quantity value must be greater than zero."))
    }
    if (quantity.unit.isNullOrBlank()) {
        errors.add(ValidationError("unit", "Quantity unit is required."))
    }
    return errors
}

fun validateTax(tax: Tax, taxableBase: Double, lineItemIndex: Int, taxIndex: Int): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (tax.category == null) {
        errors.add(ValidationError("category", "Tax category is required."))
    }
    if (tax.rate == null || tax.rate <= 0) {
        errors.add(ValidationError("rate", "Tax rate must be greater than zero."))
    }
    if (tax.amount == null) {
        errors.add(ValidationError("amount", "Tax amount is required."))
    } else {
        val expectedTax = taxableBase * (tax.rate ?: 0) / 100.0
        if (abs(expectedTax - tax.amount) > FLOAT_TOLERANCE) {
            errors.add(ValidationError("amount", "Tax amount (${tax.amount}) does not match expected value ($expectedTax) for taxable base ($taxableBase) and tax rate (${tax.rate})."))
        }
    }
    return errors
}