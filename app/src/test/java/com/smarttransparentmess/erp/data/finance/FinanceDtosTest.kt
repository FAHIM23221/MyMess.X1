package com.smarttransparentmess.erp.data.finance

import com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceDtosTest {
    @Test
    fun summaryMappingChecksBackendAndClientArithmetic() {
        val period = summaryDto().toDomain()

        assertEquals(BillingPeriodStatus.OPEN, period.periodStatus)
        assertEquals(300L, period.outstandingDueMinor)
        assertEquals(0L, period.advanceCreditMinor)
    }

    @Test
    fun inconsistentBackendTotalsAreRejected() {
        assertThrows(IllegalStateException::class.java) {
            summaryDto(outstandingDueMinor = 999).toDomain()
        }
    }

    @Test
    fun unknownPeriodStatusFailsClosed() {
        assertThrows(IllegalStateException::class.java) {
            summaryDto(status = "ARCHIVED").toDomain()
        }
    }

    @Test
    fun paymentReceiptMappingPreservesReversalAndMethod() {
        val receipt = PaymentReceiptDto(
            paymentId = "payment-1",
            billingPeriodId = "period-1",
            memberId = "member-1",
            amountMinor = 1250,
            paymentMethod = "MOBILE_BANKING",
            paidAt = "2026-10-25T10:00:00Z",
            note = "October contribution",
            transactionReference = "TX123",
            receiptId = "receipt-1",
            receiptNumber = "SM-ABC123456789",
            receiptIssuedAt = "2026-10-25T10:00:00Z",
            reversedAt = "2026-10-25T11:00:00Z",
            reversalReason = "Duplicate manual entry",
        ).toDomain()

        assertEquals(PaymentMethod.MOBILE_BANKING, receipt.method)
        assertEquals(1250L, receipt.amountMinor)
        assertEquals("Duplicate manual entry", receipt.reversalReason)
    }

    private fun summaryDto(
        status: String = "OPEN",
        outstandingDueMinor: Long = 300,
    ) = FinanceSummaryDto(
        billingPeriodId = "period-1",
        periodLabel = "October 2026",
        periodStart = "2026-10-01",
        periodEnd = "2026-10-31",
        periodStatus = status,
        memberId = "member-1",
        openingDueMinor = 100,
        chargesMinor = 1000,
        debitAdjustmentsMinor = 50,
        creditAdjustmentsMinor = 50,
        validPaymentsMinor = 800,
        outstandingDueMinor = outstandingDueMinor,
        advanceCreditMinor = 0,
    )
}
