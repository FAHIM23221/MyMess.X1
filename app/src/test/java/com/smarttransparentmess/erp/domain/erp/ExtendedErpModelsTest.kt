package com.smarttransparentmess.erp.domain.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedErpModelsTest {
    @Test
    fun fineInputIsTrimmedAndValidated() {
        val result = ExtendedErpValidation.validate(
            ErpAction.CREATE_FINE,
            mapOf(
                "member_id" to " member ",
                "period_id" to " period ",
                "amount_minor" to "1250",
                "reason" to "  A valid fine reason  ",
                "due_date" to "2026-10-31",
            ),
        )
        assertEquals("member", result.getValue("member_id"))
        assertEquals("A valid fine reason", result.getValue("reason"))
    }

    @Test
    fun rejectedReviewRequiresReason() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.REVIEW_PAYMENT,
                mapOf("payment_id" to "p", "status" to "REJECTED", "reason" to "short"),
            )
        }.exceptionOrNull()
        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REASON, (error as ExtendedErpValidationException).error)
    }

    @Test
    fun mealBillRejectsMalformedDateAndNonPositivePrice() {
        val dateError = runCatching {
            ExtendedErpValidation.validate(ErpAction.GENERATE_MEAL_BILL, mapOf("period_id" to "p", "due_date" to "31-10-2026"))
        }.exceptionOrNull()
        assertTrue(dateError is ExtendedErpValidationException)
        val amountError = runCatching {
            ExtendedErpValidation.validate(ErpAction.SET_MEAL_BILLING_POLICY, mapOf("price_per_half_meal_minor" to "0", "enabled" to "true"))
        }.exceptionOrNull()
        assertTrue(amountError is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.AMOUNT, (amountError as ExtendedErpValidationException).error)
    }

    @Test
    fun recordProjectionHandlesMissingValues() {
        val record = ErpRecord("id", mapOf("amount_minor" to "100", "enabled" to "true"))
        assertEquals(100L, record.long("amount_minor"))
        assertTrue(record.boolean("enabled"))
        assertEquals("", record.text("missing"))
    }
}
