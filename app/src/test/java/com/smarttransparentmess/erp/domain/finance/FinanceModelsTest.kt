package com.smarttransparentmess.erp.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceModelsTest {
    @Test
    fun openingDueChargesAdjustmentsAndPartialPaymentProduceExactDue() {
        val period = period(
            openingDueMinor = 1_000,
            chargesMinor = 5_000,
            debitAdjustmentsMinor = 200,
            validPaymentsMinor = 3_000,
            creditAdjustmentsMinor = 100,
        )

        assertEquals(3_100L, period.netDueMinor)
        assertEquals(3_100L, period.outstandingDueMinor)
        assertEquals(0L, period.advanceCreditMinor)
    }

    @Test
    fun overpaymentBecomesAdvanceCreditInsteadOfNegativeDue() {
        val period = period(chargesMinor = 2_500, validPaymentsMinor = 3_000)

        assertEquals(-500L, period.netDueMinor)
        assertEquals(0L, period.outstandingDueMinor)
        assertEquals(500L, period.advanceCreditMinor)
    }

    @Test
    fun creditAdjustmentCanOffsetOpeningDue() {
        val period = period(openingDueMinor = 1_200, creditAdjustmentsMinor = 1_200)

        assertEquals(0L, period.netDueMinor)
        assertEquals(0L, period.outstandingDueMinor)
        assertEquals(0L, period.advanceCreditMinor)
    }

    @Test
    fun arithmeticOverflowIsNeverSilentlyWrapped() {
        val period = period(openingDueMinor = Long.MAX_VALUE, chargesMinor = 1)
        assertThrows(ArithmeticException::class.java) { period.netDueMinor }
    }

    @Test
    fun reversedReceiptRemainsVisibleButIsNotValid() {
        val payment = PaymentReceipt(
            paymentId = "payment-1",
            billingPeriodId = "period-1",
            amountMinor = 500,
            method = PaymentMethod.CASH,
            paidAt = "2026-10-25T10:00:00Z",
            note = null,
            transactionReference = null,
            receiptId = "receipt-1",
            receiptNumber = "SM-ABC123456789",
            receiptIssuedAt = "2026-10-25T10:00:00Z",
            reversedAt = "2026-10-25T11:00:00Z",
            reversalReason = "Entered against the wrong member",
        )

        assertFalse(payment.isValid)
        assertTrue(payment.copy(reversedAt = null, reversalReason = null).isValid)
    }

    private fun period(
        openingDueMinor: Long = 0,
        chargesMinor: Long = 0,
        debitAdjustmentsMinor: Long = 0,
        creditAdjustmentsMinor: Long = 0,
        validPaymentsMinor: Long = 0,
    ) = PeriodFinance(
        billingPeriodId = "period-1",
        periodLabel = "October 2026",
        periodStart = "2026-10-01",
        periodEnd = "2026-10-31",
        periodStatus = BillingPeriodStatus.OPEN,
        openingDueMinor = openingDueMinor,
        chargesMinor = chargesMinor,
        debitAdjustmentsMinor = debitAdjustmentsMinor,
        creditAdjustmentsMinor = creditAdjustmentsMinor,
        validPaymentsMinor = validPaymentsMinor,
    )
}
