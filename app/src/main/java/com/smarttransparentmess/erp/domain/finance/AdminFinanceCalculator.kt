package com.smarttransparentmess.erp.domain.finance

import com.smarttransparentmess.erp.domain.model.AccountStatus

data class AdminFinanceOverview(
    val activeMembers: Int,
    val chargesMinor: Long,
    val validPaymentsMinor: Long,
    val openingDueMinor: Long,
    val debitAdjustmentsMinor: Long,
    val creditAdjustmentsMinor: Long,
    val outstandingDueMinor: Long,
    val advanceCreditMinor: Long,
)

object AdminFinanceCalculator {
    fun calculate(snapshot: AdminFinanceSnapshot, billingPeriodId: String): AdminFinanceOverview {
        val period = snapshot.periods.firstOrNull { it.id == billingPeriodId }
            ?: return AdminFinanceOverview(
                activeMembers = snapshot.members.count { it.accountStatus == AccountStatus.ACTIVE },
                chargesMinor = 0,
                validPaymentsMinor = 0,
                openingDueMinor = 0,
                debitAdjustmentsMinor = 0,
                creditAdjustmentsMinor = 0,
                outstandingDueMinor = 0,
                advanceCreditMinor = 0,
            )

        val charges = snapshot.charges.filter { it.billingPeriodId == billingPeriodId }
        val payments = snapshot.payments.filter { it.payment.billingPeriodId == billingPeriodId }
        val adjustments = snapshot.adjustments.filter { it.billingPeriodId == billingPeriodId }
        val memberPeriods = mutableSetOf<Pair<String, String>>().apply {
            charges.forEach { add(it.memberId to it.billingPeriodId) }
            payments.forEach { add(it.memberId to it.payment.billingPeriodId) }
            adjustments.forEach { add(it.memberId to it.billingPeriodId) }
        }

        var outstanding = 0L
        var advance = 0L
        memberPeriods.forEach { (memberId, _) ->
            val memberCharges = Money.sum(charges.filter { it.memberId == memberId }.map { it.amountMinor })
            val memberPayments = Money.sum(
                payments.filter { it.memberId == memberId && it.payment.isValid }.map { it.payment.amountMinor },
            )
            val opening = Money.sum(
                adjustments.filter { it.memberId == memberId && it.kind == AdjustmentKind.OPENING_DUE }
                    .map { it.amountMinor },
            )
            val debit = Money.sum(
                adjustments.filter { it.memberId == memberId && it.kind == AdjustmentKind.DEBIT_ADJUSTMENT }
                    .map { it.amountMinor },
            )
            val credit = Money.sum(
                adjustments.filter { it.memberId == memberId && it.kind == AdjustmentKind.CREDIT }
                    .map { it.amountMinor },
            )
            val balance = PeriodFinance(
                billingPeriodId = period.id,
                periodLabel = period.label,
                periodStart = period.startsOn,
                periodEnd = period.endsOn,
                periodStatus = period.status,
                openingDueMinor = opening,
                chargesMinor = memberCharges,
                debitAdjustmentsMinor = debit,
                creditAdjustmentsMinor = credit,
                validPaymentsMinor = memberPayments,
            )
            outstanding = Math.addExact(outstanding, balance.outstandingDueMinor)
            advance = Math.addExact(advance, balance.advanceCreditMinor)
        }

        return AdminFinanceOverview(
            activeMembers = snapshot.members.count { it.accountStatus == AccountStatus.ACTIVE },
            chargesMinor = Money.sum(charges.map { it.amountMinor }),
            validPaymentsMinor = Money.sum(payments.filter { it.payment.isValid }.map { it.payment.amountMinor }),
            openingDueMinor = Money.sum(
                adjustments.filter { it.kind == AdjustmentKind.OPENING_DUE }.map { it.amountMinor },
            ),
            debitAdjustmentsMinor = Money.sum(
                adjustments.filter { it.kind == AdjustmentKind.DEBIT_ADJUSTMENT }.map { it.amountMinor },
            ),
            creditAdjustmentsMinor = Money.sum(
                adjustments.filter { it.kind == AdjustmentKind.CREDIT }.map { it.amountMinor },
            ),
            outstandingDueMinor = outstanding,
            advanceCreditMinor = advance,
        )
    }
}
