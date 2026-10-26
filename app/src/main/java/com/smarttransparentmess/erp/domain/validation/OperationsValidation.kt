package com.smarttransparentmess.erp.domain.validation

import com.smarttransparentmess.erp.domain.finance.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class OperationsValidationError {
    REQUIRED_SELECTION,
    AMOUNT,
    MEAL_QUANTITY,
    RICE_QUANTITY,
    NOTE,
    DESCRIPTION,
    REFERENCE,
    DATE,
    FUTURE_DATE,
    REVERSAL_REASON,
}

class OperationsValidationException(
    val error: OperationsValidationError,
) : IllegalArgumentException(error.name)

object OperationsValidation {
    fun requireSelection(value: String): String {
        val selected = value.trim()
        if (selected.isEmpty()) fail(OperationsValidationError.REQUIRED_SELECTION)
        return selected
    }

    fun expenseAmountMinor(value: String): Long {
        val amount = try {
            Money.parseToMinor(value)
        } catch (_: Exception) {
            fail(OperationsValidationError.AMOUNT)
        }
        if (amount <= 0L || amount > 1_000_000_000_000L) fail(OperationsValidationError.AMOUNT)
        return amount
    }

    fun mealQuantityUnits(value: String): Int {
        val meals = value.trim().toBigDecimalOrNull() ?: fail(OperationsValidationError.MEAL_QUANTITY)
        val units = try {
            meals.multiply(BigDecimal("2")).intValueExact()
        } catch (_: ArithmeticException) {
            fail(OperationsValidationError.MEAL_QUANTITY)
        }
        if (units !in 1..20) fail(OperationsValidationError.MEAL_QUANTITY)
        return units
    }

    fun riceQuantityGrams(value: String): Int {
        val grams = try {
            value.trim().toBigDecimalOrNull()?.intValueExact()
        } catch (_: ArithmeticException) {
            null
        } ?: fail(OperationsValidationError.RICE_QUANTITY)
        if (grams !in 1..1_000_000_000) fail(OperationsValidationError.RICE_QUANTITY)
        return grams
    }

    fun requiredNote(value: String): String {
        val note = value.trim()
        if (note.length !in 2..240) fail(OperationsValidationError.NOTE)
        return note
    }

    fun optionalMealNote(value: String): String? {
        val note = value.trim()
        if (note.length > 240) fail(OperationsValidationError.NOTE)
        return note.ifEmpty { null }
    }

    fun expenseDescription(value: String): String {
        val description = value.trim()
        if (description.length !in 2..240) fail(OperationsValidationError.DESCRIPTION)
        return description
    }

    fun transactionReference(value: String): String? {
        val reference = value.trim()
        if (reference.isNotEmpty() && reference.length !in 2..120) {
            fail(OperationsValidationError.REFERENCE)
        }
        return reference.ifEmpty { null }
    }

    fun pastOrToday(value: String, today: LocalDate): LocalDate {
        val date = try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            fail(OperationsValidationError.DATE)
        }
        if (date.isAfter(today)) fail(OperationsValidationError.FUTURE_DATE)
        return date
    }

    fun reversalReason(value: String): String {
        val reason = value.trim()
        if (reason.length !in 8..500) fail(OperationsValidationError.REVERSAL_REASON)
        return reason
    }

    private fun fail(error: OperationsValidationError): Nothing = throw OperationsValidationException(error)
}
