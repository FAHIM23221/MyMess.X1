package com.smarttransparentmess.erp.domain.finance

import com.smarttransparentmess.erp.domain.model.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdminFinanceCalculatorTest {
    @Test
    fun oneMembersAdvanceDoesNotCancelAnotherMembersDue() {
        val snapshot = snapshot(
            charges = listOf(charge("charge-a", "member-a", 1_000), charge("charge-b", "member-b", 1_000)),
            payments = listOf(payment("payment-a", "member-a", 2_000)),
        )

        val overview = AdminFinanceCalculator.calculate(snapshot, "period-1")

        assertEquals(2_000L, overview.chargesMinor)
        assertEquals(2_000L, overview.validPaymentsMinor)
        assertEquals(1_000L, overview.outstandingDueMinor)
        assertEquals(1_000L, overview.advanceCreditMinor)
    }

    @Test
    fun reversedPaymentRemainsInHistoryButDoesNotReduceDue() {
        val snapshot = snapshot(
            charges = listOf(charge("charge-a", "member-a", 1_000)),
            payments = listOf(payment("payment-a", "member-a", 1_000, reversed = true)),
        )

        val overview = AdminFinanceCalculator.calculate(snapshot, "period-1")

        assertEquals(1, snapshot.payments.size)
        assertEquals(0L, overview.validPaymentsMinor)
        assertEquals(1_000L, overview.outstandingDueMinor)
        assertEquals(0L, overview.advanceCreditMinor)
    }

    @Test
    fun openingDueAndBothAdjustmentKindsAreExact() {
        val snapshot = snapshot(
            charges = listOf(charge("charge-a", "member-a", 500)),
            adjustments = listOf(
                adjustment("adjustment-1", AdjustmentKind.OPENING_DUE, 1_000),
                adjustment("adjustment-2", AdjustmentKind.DEBIT_ADJUSTMENT, 200),
                adjustment("adjustment-3", AdjustmentKind.CREDIT, 100),
            ),
            payments = listOf(payment("payment-a", "member-a", 300)),
        )

        val overview = AdminFinanceCalculator.calculate(snapshot, "period-1")

        assertEquals(1_000L, overview.openingDueMinor)
        assertEquals(200L, overview.debitAdjustmentsMinor)
        assertEquals(100L, overview.creditAdjustmentsMinor)
        assertEquals(1_300L, overview.outstandingDueMinor)
        assertEquals(0L, overview.advanceCreditMinor)
    }

    @Test
    fun missingPeriodReturnsZeroBalancesWithoutInventingActivity() {
        val overview = AdminFinanceCalculator.calculate(snapshot(), "missing")

        assertEquals(2, overview.activeMembers)
        assertEquals(0L, overview.chargesMinor)
        assertEquals(0L, overview.validPaymentsMinor)
        assertEquals(0L, overview.outstandingDueMinor)
        assertEquals(0L, overview.advanceCreditMinor)
    }

    @Test
    fun aggregateOverflowIsNotSilentlyWrapped() {
        assertThrows(ArithmeticException::class.java) {
            Money.sum(listOf(Long.MAX_VALUE, 1L))
        }
    }

    private fun snapshot(
        charges: List<ChargeRecord> = emptyList(),
        adjustments: List<AdjustmentRecord> = emptyList(),
        payments: List<AdminPaymentReceipt> = emptyList(),
    ) = AdminFinanceSnapshot(
        periods = listOf(
            BillingPeriod("period-1", "October 2026", "2026-10-01", "2026-10-31", BillingPeriodStatus.OPEN),
        ),
        members = listOf(
            FinanceMember("member-a", "M-001", "Member A", AccountStatus.ACTIVE),
            FinanceMember("member-b", "M-002", "Member B", AccountStatus.ACTIVE),
        ),
        charges = charges,
        adjustments = adjustments,
        payments = payments,
        loadedAt = "2026-10-25T12:00:00Z",
    )

    private fun charge(id: String, memberId: String, amount: Long) = ChargeRecord(
        id = id,
        memberId = memberId,
        memberName = memberId,
        billingPeriodId = "period-1",
        periodLabel = "October 2026",
        category = ChargeCategory.SEAT_RENT,
        amountMinor = amount,
        description = "Seat rent",
        dueDate = null,
        createdAt = "2026-10-25T10:00:00Z",
    )

    private fun adjustment(id: String, kind: AdjustmentKind, amount: Long) = AdjustmentRecord(
        id = id,
        memberId = "member-a",
        memberName = "Member A",
        billingPeriodId = "period-1",
        periodLabel = "October 2026",
        kind = kind,
        amountMinor = amount,
        reason = "Audited adjustment",
        createdAt = "2026-10-25T10:00:00Z",
    )

    private fun payment(id: String, memberId: String, amount: Long, reversed: Boolean = false) =
        AdminPaymentReceipt(
            memberId = memberId,
            memberName = memberId,
            periodLabel = "October 2026",
            payment = PaymentReceipt(
                paymentId = id,
                billingPeriodId = "period-1",
                amountMinor = amount,
                method = PaymentMethod.CASH,
                paidAt = "2026-10-25T10:00:00Z",
                note = null,
                transactionReference = null,
                receiptId = "receipt-$id",
                receiptNumber = "SM-$id",
                receiptIssuedAt = "2026-10-25T10:00:00Z",
                reversedAt = if (reversed) "2026-10-25T11:00:00Z" else null,
                reversalReason = if (reversed) "Wrong member entry" else null,
            ),
        )
}
