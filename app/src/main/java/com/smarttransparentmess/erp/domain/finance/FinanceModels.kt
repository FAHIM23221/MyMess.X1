package com.smarttransparentmess.erp.domain.finance

enum class BillingPeriodStatus {
    OPEN,
    CLOSED;

    companion object {
        fun fromDatabase(value: String): BillingPeriodStatus? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

enum class PaymentMethod {
    CASH,
    BANK,
    MOBILE_BANKING,
    OTHER;

    companion object {
        fun fromDatabase(value: String): PaymentMethod? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

data class PeriodFinance(
    val billingPeriodId: String,
    val periodLabel: String,
    val periodStart: String,
    val periodEnd: String,
    val periodStatus: BillingPeriodStatus,
    val openingDueMinor: Long,
    val chargesMinor: Long,
    val debitAdjustmentsMinor: Long,
    val creditAdjustmentsMinor: Long,
    val validPaymentsMinor: Long,
) {
    init {
        require(
            openingDueMinor >= 0 && chargesMinor >= 0 && debitAdjustmentsMinor >= 0 &&
                creditAdjustmentsMinor >= 0 && validPaymentsMinor >= 0,
        ) { "Ledger totals cannot be negative." }
    }

    /** Positive means the member owes the mess; negative means the member has advance credit. */
    val netDueMinor: Long
        get() = Math.subtractExact(
            Math.addExact(
                Math.addExact(openingDueMinor, chargesMinor),
                debitAdjustmentsMinor,
            ),
            Math.addExact(validPaymentsMinor, creditAdjustmentsMinor),
        )

    val outstandingDueMinor: Long
        get() = netDueMinor.coerceAtLeast(0L)

    val advanceCreditMinor: Long
        get() = if (netDueMinor < 0L) Math.negateExact(netDueMinor) else 0L
}

data class PaymentReceipt(
    val paymentId: String,
    val billingPeriodId: String,
    val amountMinor: Long,
    val method: PaymentMethod,
    val paidAt: String,
    val note: String?,
    val transactionReference: String?,
    val receiptId: String,
    val receiptNumber: String,
    val receiptIssuedAt: String,
    val reversedAt: String?,
    val reversalReason: String?,
) {
    init {
        require(amountMinor > 0L) { "A payment must have a positive amount." }
    }

    val isValid: Boolean
        get() = reversedAt == null
}

data class MemberFinance(
    val currentPeriod: PeriodFinance?,
    val recentPayments: List<PaymentReceipt>,
)
