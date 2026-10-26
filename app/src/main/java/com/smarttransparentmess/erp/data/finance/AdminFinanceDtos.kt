package com.smarttransparentmess.erp.data.finance

import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.AdjustmentRecord
import com.smarttransparentmess.erp.domain.finance.BillingPeriod
import com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.ChargeRecord
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.domain.finance.PaymentReceipt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BillingPeriodDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    val label: String,
    @SerialName("starts_on") val startsOn: String,
    @SerialName("ends_on") val endsOn: String,
    val status: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("closed_by") val closedBy: String? = null,
) {
    fun toDomain() = BillingPeriod(
        id = id,
        label = label,
        startsOn = startsOn,
        endsOn = endsOn,
        status = BillingPeriodStatus.fromDatabase(status) ?: error("Unsupported period status: $status"),
    )
}

@Serializable
data class PaymentRowDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("billing_period_id") val billingPeriodId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("paid_at") val paidAt: String,
    val note: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("recorded_by") val recordedBy: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ReceiptRowDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("payment_id") val paymentId: String,
    @SerialName("receipt_number") val receiptNumber: String,
    @SerialName("issued_at") val issuedAt: String,
    @SerialName("voided_at") val voidedAt: String? = null,
    @SerialName("voided_by") val voidedBy: String? = null,
    @SerialName("void_reason") val voidReason: String? = null,
)

@Serializable
data class ReversalRowDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("payment_id") val paymentId: String,
    val reason: String,
    @SerialName("reversed_by") val reversedBy: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChargeRowDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("billing_period_id") val billingPeriodId: String,
    @SerialName("member_id") val memberId: String,
    val category: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val description: String,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(memberName: String, periodLabel: String) = ChargeRecord(
        id = id,
        memberId = memberId,
        memberName = memberName,
        billingPeriodId = billingPeriodId,
        periodLabel = periodLabel,
        category = ChargeCategory.fromDatabase(category) ?: error("Unsupported charge category: $category"),
        amountMinor = amountMinor,
        description = description,
        dueDate = dueDate,
        createdAt = createdAt,
    )
}

@Serializable
data class AdjustmentRowDto(
    val id: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("billing_period_id") val billingPeriodId: String,
    @SerialName("member_id") val memberId: String,
    val kind: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val reason: String,
    @SerialName("source_period_id") val sourcePeriodId: String? = null,
    @SerialName("client_request_id") val clientRequestId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(memberName: String, periodLabel: String) = AdjustmentRecord(
        id = id,
        memberId = memberId,
        memberName = memberName,
        billingPeriodId = billingPeriodId,
        periodLabel = periodLabel,
        kind = AdjustmentKind.fromDatabase(kind) ?: error("Unsupported adjustment kind: $kind"),
        amountMinor = amountMinor,
        reason = reason,
        createdAt = createdAt,
    )
}

internal fun PaymentRowDto.toDomainPayment(
    receipt: ReceiptRowDto,
    reversal: ReversalRowDto?,
): PaymentReceipt {
    check(receipt.paymentId == id && receipt.memberId == memberId && receipt.messId == messId) {
        "Payment and receipt identities are inconsistent."
    }
    check((receipt.voidedAt == null) == (reversal == null)) {
        "Receipt void and reversal states are inconsistent."
    }
    return PaymentReceipt(
        paymentId = id,
        billingPeriodId = billingPeriodId,
        amountMinor = amountMinor,
        method = PaymentMethod.fromDatabase(paymentMethod)
            ?: error("Unsupported payment method: $paymentMethod"),
        paidAt = paidAt,
        note = note,
        transactionReference = transactionReference,
        receiptId = receipt.id,
        receiptNumber = receipt.receiptNumber,
        receiptIssuedAt = receipt.issuedAt,
        reversedAt = reversal?.createdAt,
        reversalReason = reversal?.reason,
    )
}
