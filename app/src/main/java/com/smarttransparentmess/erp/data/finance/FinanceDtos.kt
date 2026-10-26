package com.smarttransparentmess.erp.data.finance

import com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.domain.finance.PaymentReceipt
import com.smarttransparentmess.erp.domain.finance.PeriodFinance
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FinanceSummaryDto(
    @SerialName("billing_period_id") val billingPeriodId: String,
    @SerialName("period_label") val periodLabel: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("period_status") val periodStatus: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("opening_due_minor") val openingDueMinor: Long,
    @SerialName("charges_minor") val chargesMinor: Long,
    @SerialName("debit_adjustments_minor") val debitAdjustmentsMinor: Long,
    @SerialName("credit_adjustments_minor") val creditAdjustmentsMinor: Long,
    @SerialName("valid_payments_minor") val validPaymentsMinor: Long,
    @SerialName("outstanding_due_minor") val outstandingDueMinor: Long,
    @SerialName("advance_credit_minor") val advanceCreditMinor: Long,
) {
    fun toDomain(): PeriodFinance {
        val domain = PeriodFinance(
            billingPeriodId = billingPeriodId,
            periodLabel = periodLabel,
            periodStart = periodStart,
            periodEnd = periodEnd,
            periodStatus = BillingPeriodStatus.fromDatabase(periodStatus)
                ?: error("Unsupported billing period status: $periodStatus"),
            openingDueMinor = openingDueMinor,
            chargesMinor = chargesMinor,
            debitAdjustmentsMinor = debitAdjustmentsMinor,
            creditAdjustmentsMinor = creditAdjustmentsMinor,
            validPaymentsMinor = validPaymentsMinor,
        )
        check(domain.outstandingDueMinor == outstandingDueMinor && domain.advanceCreditMinor == advanceCreditMinor) {
            "The backend financial totals are internally inconsistent."
        }
        return domain
    }
}

@Serializable
data class PaymentReceiptDto(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("billing_period_id") val billingPeriodId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("paid_at") val paidAt: String,
    @SerialName("note") val note: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    @SerialName("receipt_id") val receiptId: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("receipt_issued_at") val receiptIssuedAt: String,
    @SerialName("reversed_at") val reversedAt: String? = null,
    @SerialName("reversal_reason") val reversalReason: String? = null,
) {
    fun toDomain(): PaymentReceipt = PaymentReceipt(
        paymentId = paymentId,
        billingPeriodId = billingPeriodId,
        amountMinor = amountMinor,
        method = PaymentMethod.fromDatabase(paymentMethod)
            ?: error("Unsupported payment method: $paymentMethod"),
        paidAt = paidAt,
        note = note,
        transactionReference = transactionReference,
        receiptId = receiptId,
        receiptNumber = receiptNumber,
        receiptIssuedAt = receiptIssuedAt,
        reversedAt = reversedAt,
        reversalReason = reversalReason,
    )
}
