package com.smarttransparentmess.erp.domain.validation

import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceEntryValidatorTest {
    private val now: ZonedDateTime = ZonedDateTime.parse("2026-10-25T12:00:00+06:00")

    @Test
    fun paymentUsesDhakaStartOfDayAndNormalizesOptionalText() {
        val entry = FinanceEntryValidator.payment(
            memberId = "member-1",
            billingPeriodId = "period-1",
            amount = " 1250.50 ",
            method = PaymentMethod.BKASH,
            paidOn = "2026-10-25",
            note = "  Monthly payment  ",
            reference = "  TXN-123  ",
            now = now,
        )

        assertEquals(125_050L, entry.amountMinor)
        assertEquals("2026-10-24T18:00:00Z", entry.paidAt)
        assertEquals("Monthly payment", entry.note)
        assertEquals("TXN-123", entry.transactionReference)
    }

    @Test
    fun blankOptionalPaymentTextBecomesNull() {
        val entry = FinanceEntryValidator.payment(
            "member-1", "period-1", "10", PaymentMethod.CASH, "2026-10-25", " ", " ", now,
        )

        assertNull(entry.note)
        assertNull(entry.transactionReference)
    }

    @Test
    fun futureDhakaPaymentDateIsRejected() {
        val error = assertThrows(FinanceValidationException::class.java) {
            FinanceEntryValidator.payment(
                "member-1", "period-1", "10", PaymentMethod.CASH, "2026-10-26", "", "", now,
            )
        }

        assertEquals(FinanceValidationError.FUTURE_PAYMENT, error.error)
    }

    @Test
    fun excessiveFractionAndUnsupportedAmountAreRejected() {
        listOf("0", "-1", "1.234", "10000000000.01").forEach { amount ->
            val error = assertThrows(FinanceValidationException::class.java) {
                FinanceEntryValidator.amount(amount)
            }
            assertEquals(FinanceValidationError.AMOUNT, error.error)
        }
    }

    @Test
    fun billingPeriodRequiresAnOrderedValidRange() {
        val period = FinanceEntryValidator.period(" October 2026 ", "2026-10-01", "2026-10-31")
        assertEquals("October 2026", period.label)

        val error = assertThrows(FinanceValidationException::class.java) {
            FinanceEntryValidator.period("October", "2026-10-31", "2026-10-01")
        }
        assertEquals(FinanceValidationError.DATE_RANGE, error.error)
    }

    @Test
    fun chargeAndAdjustmentTrimAndValidateTheirFields() {
        val charge = FinanceEntryValidator.charge(
            "member-1", "period-1", ChargeCategory.ELECTRICITY, "20.25", "  Shared bill  ", " ",
        )
        assertEquals(2_025L, charge.amountMinor)
        assertEquals("Shared bill", charge.description)
        assertNull(charge.dueDate)

        val adjustment = FinanceEntryValidator.adjustment(
            "member-1", "period-1", AdjustmentKind.CREDIT, "5", "  Correction approved  ",
        )
        assertEquals("Correction approved", adjustment.reason)

        assertThrows(FinanceValidationException::class.java) {
            FinanceEntryValidator.reversalReason("short")
        }
    }
}
