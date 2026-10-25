package com.smarttransparentmess.erp.domain.finance

import com.smarttransparentmess.erp.domain.model.AccountStatus

enum class ChargeCategory {
    SEAT_RENT,
    MEAL_CHARGE,
    ELECTRICITY,
    WIFI,
    GAS,
    WATER,
    FINE,
    UTILITY,
    SERVICE,
    OTHER;

    companion object {
        fun fromDatabase(value: String): ChargeCategory? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

enum class AdjustmentKind {
    OPENING_DUE,
    CREDIT,
    DEBIT_ADJUSTMENT;

    companion object {
        fun fromDatabase(value: String): AdjustmentKind? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

data class BillingPeriod(
    val id: String,
    val label: String,
    val startsOn: String,
    val endsOn: String,
    val status: BillingPeriodStatus,
)

data class FinanceMember(
    val userId: String,
    val memberId: String?,
    val fullName: String,
    val accountStatus: AccountStatus,
)

data class ChargeRecord(
    val id: String,
    val memberId: String,
    val memberName: String,
    val billingPeriodId: String,
    val periodLabel: String,
    val category: ChargeCategory,
    val amountMinor: Long,
    val description: String,
    val dueDate: String?,
    val createdAt: String,
)

data class AdjustmentRecord(
    val id: String,
    val memberId: String,
    val memberName: String,
    val billingPeriodId: String,
    val periodLabel: String,
    val kind: AdjustmentKind,
    val amountMinor: Long,
    val reason: String,
    val createdAt: String,
)

data class AdminPaymentReceipt(
    val memberId: String,
    val memberName: String,
    val periodLabel: String,
    val payment: PaymentReceipt,
)

data class AdminFinanceSnapshot(
    val periods: List<BillingPeriod>,
    val members: List<FinanceMember>,
    val charges: List<ChargeRecord>,
    val adjustments: List<AdjustmentRecord>,
    val payments: List<AdminPaymentReceipt>,
    val loadedAt: String,
)

data class PaymentEntry(
    val memberId: String,
    val billingPeriodId: String,
    val amountMinor: Long,
    val method: PaymentMethod,
    val paidAt: String,
    val note: String?,
    val transactionReference: String?,
)

data class ChargeEntry(
    val memberId: String,
    val billingPeriodId: String,
    val category: ChargeCategory,
    val amountMinor: Long,
    val description: String,
    val dueDate: String?,
)

data class AdjustmentEntry(
    val memberId: String,
    val billingPeriodId: String,
    val kind: AdjustmentKind,
    val amountMinor: Long,
    val reason: String,
)
