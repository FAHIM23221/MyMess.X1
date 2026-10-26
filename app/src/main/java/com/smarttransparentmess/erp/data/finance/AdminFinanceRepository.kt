package com.smarttransparentmess.erp.data.finance

import com.smarttransparentmess.erp.data.profile.ProfileDto
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.AdminPaymentReceipt
import com.smarttransparentmess.erp.domain.finance.AdjustmentEntry
import com.smarttransparentmess.erp.domain.finance.ChargeEntry
import com.smarttransparentmess.erp.domain.finance.FinanceMember
import com.smarttransparentmess.erp.domain.finance.PaymentEntry
import com.smarttransparentmess.erp.domain.validation.BillingPeriodEntry
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AdminFinanceRepository {
    suspend fun loadSnapshot(): AdminFinanceSnapshot
    suspend fun createBillingPeriod(entry: BillingPeriodEntry)
    suspend fun closeBillingPeriod(periodId: String)
    suspend fun createCharge(entry: ChargeEntry, clientRequestId: String)
    suspend fun addAdjustment(entry: AdjustmentEntry, clientRequestId: String)
    suspend fun recordPayment(entry: PaymentEntry, clientRequestId: String)
    suspend fun reversePayment(paymentId: String, reason: String)
    suspend fun carryForward(memberId: String, sourcePeriodId: String, targetPeriodId: String)
}

class SupabaseAdminFinanceRepository(
    private val client: SupabaseClient,
) : AdminFinanceRepository {
    override suspend fun loadSnapshot(): AdminFinanceSnapshot {
        requireSession()
        val periods = client.from("billing_periods").select().decodeList<BillingPeriodDto>()
            .map(BillingPeriodDto::toDomain)
            .sortedWith(compareByDescending<com.smarttransparentmess.erp.domain.finance.BillingPeriod> { it.startsOn }
                .thenByDescending { it.endsOn })
        val profiles = client.from("profiles").select().decodeList<ProfileDto>()
            .map(ProfileDto::toDomain)
        val members = profiles.map {
            FinanceMember(
                userId = it.userId,
                memberId = it.memberId,
                fullName = it.fullName,
                accountStatus = it.accountStatus,
            )
        }.sortedBy { it.fullName.lowercase() }

        val memberNames = members.associate { it.userId to it.fullName }
        val periodLabels = periods.associate { it.id to it.label }

        val charges = client.from("charges").select().decodeList<ChargeRowDto>()
            .map { row ->
                row.toDomain(
                    memberName = memberNames.getValue(row.memberId),
                    periodLabel = periodLabels.getValue(row.billingPeriodId),
                )
            }.sortedByDescending { it.createdAt }

        val adjustments = client.from("billing_adjustments").select().decodeList<AdjustmentRowDto>()
            .map { row ->
                row.toDomain(
                    memberName = memberNames.getValue(row.memberId),
                    periodLabel = periodLabels.getValue(row.billingPeriodId),
                )
            }.sortedByDescending { it.createdAt }

        val paymentRows = client.from("payments").select().decodeList<PaymentRowDto>()
        val receipts = client.from("receipts").select().decodeList<ReceiptRowDto>()
            .associateBy { it.paymentId }
        val reversals = client.from("payment_reversals").select().decodeList<ReversalRowDto>()
            .associateBy { it.paymentId }
        val payments = paymentRows.map { row ->
            val receipt = receipts.getValue(row.id)
            AdminPaymentReceipt(
                memberId = row.memberId,
                memberName = memberNames.getValue(row.memberId),
                periodLabel = periodLabels.getValue(row.billingPeriodId),
                payment = row.toDomainPayment(receipt, reversals[row.id]),
            )
        }.sortedByDescending { it.payment.paidAt }

        return AdminFinanceSnapshot(
            periods = periods,
            members = members,
            charges = charges,
            adjustments = adjustments,
            payments = payments,
            loadedAt = Instant.now().toString(),
        )
    }

    override suspend fun createBillingPeriod(entry: BillingPeriodEntry) {
        requireSession()
        client.postgrest.rpc(
            "create_billing_period",
            buildJsonObject {
                put("target_label", JsonPrimitive(entry.label))
                put("target_start", JsonPrimitive(entry.startsOn))
                put("target_end", JsonPrimitive(entry.endsOn))
            },
        )
    }

    override suspend fun closeBillingPeriod(periodId: String) {
        requireSession()
        client.postgrest.rpc(
            "close_billing_period",
            buildJsonObject { put("target_period_id", JsonPrimitive(periodId)) },
        )
    }

    override suspend fun createCharge(entry: ChargeEntry, clientRequestId: String) {
        requireSession()
        client.postgrest.rpc(
            "create_charge",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(entry.memberId))
                put("target_period_id", JsonPrimitive(entry.billingPeriodId))
                put("target_category", JsonPrimitive(entry.category.name))
                put("target_amount_minor", JsonPrimitive(entry.amountMinor))
                put("target_description", JsonPrimitive(entry.description))
                put("target_due_date", entry.dueDate?.let(::JsonPrimitive) ?: JsonNull)
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun addAdjustment(entry: AdjustmentEntry, clientRequestId: String) {
        requireSession()
        client.postgrest.rpc(
            "add_billing_adjustment",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(entry.memberId))
                put("target_period_id", JsonPrimitive(entry.billingPeriodId))
                put("target_kind", JsonPrimitive(entry.kind.name))
                put("target_amount_minor", JsonPrimitive(entry.amountMinor))
                put("target_reason", JsonPrimitive(entry.reason))
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun recordPayment(entry: PaymentEntry, clientRequestId: String) {
        requireSession()
        client.postgrest.rpc(
            "record_payment",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(entry.memberId))
                put("target_period_id", JsonPrimitive(entry.billingPeriodId))
                put("target_amount_minor", JsonPrimitive(entry.amountMinor))
                put("target_method", JsonPrimitive(entry.method.name))
                put("target_paid_at", JsonPrimitive(entry.paidAt))
                put("target_note", entry.note?.let(::JsonPrimitive) ?: JsonNull)
                put(
                    "target_transaction_reference",
                    entry.transactionReference?.let(::JsonPrimitive) ?: JsonNull,
                )
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun reversePayment(paymentId: String, reason: String) {
        requireSession()
        client.postgrest.rpc(
            "reverse_payment",
            buildJsonObject {
                put("target_payment_id", JsonPrimitive(paymentId))
                put("target_reason", JsonPrimitive(reason))
            },
        )
    }

    override suspend fun carryForward(memberId: String, sourcePeriodId: String, targetPeriodId: String) {
        requireSession()
        client.postgrest.rpc(
            "carry_forward_balance",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(memberId))
                put("from_period_id", JsonPrimitive(sourcePeriodId))
                put("to_period_id", JsonPrimitive(targetPeriodId))
            },
        )
    }

    private fun requireSession() {
        check(client.auth.currentUserOrNull() != null) { "An authenticated session is required." }
    }
}
