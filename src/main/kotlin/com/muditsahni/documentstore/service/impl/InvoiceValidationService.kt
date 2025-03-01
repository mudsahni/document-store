package com.muditsahni.documentstore.service.impl

import com.muditsahni.documentstore.exception.ErrorSeverity
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
        errors.add(ValidationError("invoiceNumber", "Invoice number is required.", ErrorSeverity.CRITICAL))
    }

    // Billing Date
    var billingDate = LocalDate.MIN

    if (invoice.billingDate.isNullOrBlank() || isValidDate(invoice.billingDate)) {
        errors.add(
            ValidationError(
                "billingDate",
                "Billing date is null or of an unexpected format. ${invoice.billingDate}",
                ErrorSeverity.CRITICAL
            )
        )
    } else {
        try {
            billingDate = parseDate(invoice.billingDate)
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("billingDate", "Billing date is invalid.", ErrorSeverity.CRITICAL))
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

    // IRN Number
    if (invoice.irnNumber.isNullOrBlank()) {
        errors.add(ValidationError("irnNumber", "IRN number is not present."))
    }


    // Currency Code
    if (invoice.currencyCode.isNullOrBlank() || !currencyRegex.matches(invoice.currencyCode)) {
        errors.add(ValidationError("currencyCode", "Currency code must be a valid ISO code (e.g., USD)."))
    }

    // Customer
    if (invoice.customer == null) {
        errors.add(ValidationError("customer", "Customer is required.", ErrorSeverity.CRITICAL))
    } else {
        errors.addAll(
            validateCustomer(invoice.customer)
                .map { it.copy(field = "customer.${it.field}") }
        )
    }

    // Vendor
    if (invoice.vendor == null) {
        errors.add(ValidationError("vendor", "Vendor is required.", ErrorSeverity.CRITICAL))
    } else {
        errors.addAll(
            validateVendor(invoice.vendor)
                .map { it.copy(field = "vendor.${it.field}") }
        )
    }

    // Billed Amount
    if (invoice.billedAmount == null) {
        errors.add(ValidationError("billedAmount", "Billed amount is required.", ErrorSeverity.CRITICAL))
    } else {
        errors.addAll(
            validateBilledAmount(invoice.billedAmount)
                .map { it.copy(field = "billedAmount.${it.field}") }
        )
    }

    // Line Items
    if (invoice.lineItems.isEmpty()) {
        errors.add(ValidationError("lineItems", "At least one line item is required.", ErrorSeverity.CRITICAL))
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
        if (abs(totalLineItemsAmount - billedTotal) > FLOAT_TOLERANCE * 100) {
            errors.add(
                ValidationError(
                    "billedAmount.total",
                    "Sum of line item amounts ($totalLineItemsAmount) does not match billed total ($billedTotal).",
                    ErrorSeverity.CRITICAL
                )
            )
        }
    }

    return errors
}

fun validateCustomer(customer: Customer): List<ValidationError> {

    val errors = mutableListOf<ValidationError>()
    if (customer.name.isNullOrBlank()) {
        errors.add(ValidationError("name", "Customer name is required.", ErrorSeverity.CRITICAL))
    }
    if (customer.billingAddress.isNullOrBlank()) {
        errors.add(ValidationError("billingAddress", "Customer billing address is required.", ErrorSeverity.MAJOR))
    }

    if (customer.shippingAddress.isNullOrBlank()) {
        errors.add(ValidationError("shippingAddress", "Customer shipping address is required."))
    }

    if (!customer.gstNumber.isNullOrBlank() && !gstRegex.matches(customer.gstNumber)) {
        errors.add(ValidationError("gstNumber", "Customer GST number is invalid.", ErrorSeverity.CRITICAL))
    }

    if (customer.pan.isNullOrBlank() || !panRegex.matches(customer.pan)) {
        errors.add(ValidationError("pan", "Customer PAN is null or invalid."))
    }

    return errors
}

fun validateVendor(vendor: Vendor): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    if (vendor.name.isNullOrBlank()) {
        errors.add(ValidationError("name", "Vendor name is required.", ErrorSeverity.CRITICAL))
    }
    if (vendor.address.isNullOrBlank()) {
        errors.add(ValidationError("address", "Vendor address is required.", ErrorSeverity.CRITICAL))
    }
    if (vendor.gstNumber.isNullOrBlank() || !gstRegex.matches(vendor.gstNumber)) {
        errors.add(ValidationError("gstNumber", "Vendor GST number is invalid.", ErrorSeverity.CRITICAL))
    }
    if (vendor.pan.isNullOrBlank() || !panRegex.matches(vendor.pan)) {
        errors.add(ValidationError("pan", "Vendor PAN is invalid.", ErrorSeverity.MAJOR))
    }

    if (vendor.upiId.isNullOrBlank() || !upiIdRegex.matches(vendor.upiId)) {
        errors.add(ValidationError("upiId", "Vendor UPI Id is missing or invalid."))
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
        errors.add(ValidationError("bankName", "Bank name is required.", ErrorSeverity.CRITICAL))
    }

    if (bankDetail.branch.isNullOrBlank()) {
        errors.add(ValidationError("branch", "Branch name is required."))
    }

    if (bankDetail.accountNumber.isNullOrBlank()) {
        errors.add(ValidationError("accountNumber", "Account number is required.", ErrorSeverity.CRITICAL))
    }
    if (bankDetail.branchAddress.isNullOrBlank()) {
        errors.add(ValidationError("branchAddress", "Branch address is required."))
    }
    if (bankDetail.ifsc.isNullOrBlank()) {
        errors.add(ValidationError("ifsc", "IFSC code is required.", ErrorSeverity.CRITICAL))
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
        errors.add(ValidationError("total", "Total must be non-negative.", ErrorSeverity.CRITICAL))
    }
    if (billedAmount.balanceDue == null || billedAmount.balanceDue < 0) {
        errors.add(ValidationError("balanceDue", "Balance due must be non-negative."))
    }
    if (billedAmount.previousDues == null || billedAmount.previousDues < 0) {
        errors.add(ValidationError("previousDues", "Previous dues is null or invalid."))
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
        errors.add(ValidationError("description", "Description is required.", ErrorSeverity.CRITICAL))
    }

    if (item.hsnSac.isNullOrBlank()) {
        errors.add(ValidationError("hsnSac", "HSN/SAC id is required.", ErrorSeverity.CRITICAL))
    }

    if (item.discount == null) {
        errors.add(ValidationError("discount", "Discount is required."))
        errors.add(ValidationError("discount.percentage", "Discount percentage is required."))
        errors.add(ValidationError("discount.amount", "Discount amount is required."))
    }
    if (item.quantity == null) {
        errors.add(ValidationError("quantity", "Quantity is required."))
    } else {
        errors.addAll(validateQuantity(item.quantity, index).map { it.copy(field = "quantity.${it.field}") })
    }

    val isLineItemRateValid = item.rate != null && item.rate > 0
    val isLineItemAmountValid = item.amount != null && item.amount > 0

    if (!isLineItemRateValid && isLineItemAmountValid) {
        errors.add(ValidationError("rate", "Rate must be provided and greater than zero.", ErrorSeverity.MINOR))
    }

    if (isLineItemRateValid && !isLineItemAmountValid) {
        errors.add(ValidationError("amount", "Amount must be provided and greater than zero.", ErrorSeverity.MINOR))
    }

    if (!isLineItemRateValid && !isLineItemAmountValid) {
        errors.add(ValidationError("rate", "Rate must be provided and greater than zero.", ErrorSeverity.CRITICAL))
        errors.add(ValidationError("amount", "Amount must be provided and greater than zero.", ErrorSeverity.CRITICAL))
    }



    if (item.rate == null || item.rate <= 0) {
        errors.add(ValidationError("rate", "Rate must be provided and greater than zero.", ErrorSeverity.CRITICAL))
    }
    if (item.amount == null) {
        errors.add(ValidationError("amount", "Amount is required.", ErrorSeverity.CRITICAL))
    } else {
        // Calculate expected amount: quantity.value * rate - discount + taxes
        val quantityValue = item.quantity?.value ?: 0.0
        val rate = item.rate ?: 0.0

        // Validate discount
        if (item.discount != null) {
            errors.addAll(
                validateDiscount(
                    item.discount,
                    quantityValue * rate,
                    index
                ).map { it.copy(field = "discount.${it.field}") })
        }

        val discountAmount = if (item.discount != null) {
            if (item.discount.percentage != null) {
                quantityValue * rate * item.discount.percentage / 100.0
            } else {
                item.discount.amount ?: 0.0
            }
        } else 0.0

        val taxTotal = item.taxes.sumOf { it.amount ?: 0.0 }
        val expectedAmount = quantityValue * rate - discountAmount + taxTotal
        if (abs(expectedAmount - item.amount) > FLOAT_TOLERANCE * 100) {
            errors.add(
                ValidationError(
                    "amount",
                    "Amount (${item.amount}) does not match expected value ($expectedAmount) based on quantity, rate, discount, and taxes.",
                    ErrorSeverity.CRITICAL
                )
            )
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
    if (discount.amount == null) {
        errors.add(ValidationError("amount", "Discount amount is required."))
    }

    if (discount.percentage == null) {
        errors.add(ValidationError("percentage", "Discount percentage is required."))
    }

    if (discount.percentage != null && discount.amount != null) {
        val expectedDiscountAmount = baseAmount * discount.percentage / 100.0
        if (abs(expectedDiscountAmount - discount.amount) > FLOAT_TOLERANCE) {
            errors.add(
                ValidationError(
                    "amount",
                    "Discount amount (${discount.amount}) does not match discount percentage (${discount.percentage}) on base amount ($baseAmount). Expected: $expectedDiscountAmount",
                    ErrorSeverity.MAJOR
                )
            )
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