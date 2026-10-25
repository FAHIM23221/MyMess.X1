package com.smarttransparentmess.erp.data.operations

import com.smarttransparentmess.erp.data.profile.ProfileDto
import com.smarttransparentmess.erp.domain.operations.CashBookDirection
import com.smarttransparentmess.erp.domain.operations.CashBookEntry
import com.smarttransparentmess.erp.domain.operations.ExpenseCategory
import com.smarttransparentmess.erp.domain.operations.ExpenseRecord
import com.smarttransparentmess.erp.domain.operations.ExpenseReversal
import com.smarttransparentmess.erp.domain.operations.MealEntry
import com.smarttransparentmess.erp.domain.operations.MealReversal
import com.smarttransparentmess.erp.domain.operations.MealSlot
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.domain.operations.RiceMovement
import com.smarttransparentmess.erp.domain.operations.RiceMovementKind
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface OperationsRepository {
    suspend fun loadSnapshot(): OperationsSnapshot
    suspend fun recordMeal(
        memberId: String,
        mealDate: String,
        slot: MealSlot,
        quantityUnits: Int,
        note: String?,
        clientRequestId: String,
    )
    suspend fun reverseMeal(entryId: String, reason: String)
    suspend fun recordRice(
        kind: RiceMovementKind,
        quantityGrams: Int,
        note: String,
        clientRequestId: String,
    )
    suspend fun recordExpense(
        category: ExpenseCategory,
        amountMinor: Long,
        spentOn: String,
        description: String,
        reference: String?,
        clientRequestId: String,
    )
    suspend fun reverseExpense(expenseId: String, reason: String)
}

class SupabaseOperationsRepository(
    private val client: SupabaseClient,
) : OperationsRepository {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    override suspend fun loadSnapshot(): OperationsSnapshot {
        requireSession()
        val profiles = client.from("profiles").select().decodeList<ProfileDto>()
            .map(ProfileDto::toDomain)
            .sortedBy { it.fullName.lowercase() }
        val memberNames = profiles.associate { it.userId to it.fullName }

        val meals = client.from("meal_entries").select().decodeList<MealEntryDto>()
            .sortedWith(
                compareByDescending<MealEntryDto> { it.mealDate }
                    .thenByDescending { Instant.parse(it.createdAt) },
            )
            .map { row ->
                MealEntry(
                    id = row.id,
                    memberId = row.memberId,
                    memberName = memberNames[row.memberId] ?: row.memberId,
                    mealDate = row.mealDate,
                    slot = MealSlot.valueOf(row.slot),
                    quantityUnits = row.quantityUnits,
                    note = row.note,
                    createdAt = displayTime(row.createdAt.toString()),
                )
            }
        val mealReversals = client.from("meal_reversals").select().decodeList<MealReversalDto>()
            .sortedByDescending { Instant.parse(it.createdAt) }
            .map { row ->
                MealReversal(row.mealEntryId, row.reason, displayTime(row.createdAt.toString()))
            }
        val riceMovements = client.from("rice_movements").select().decodeList<RiceMovementDto>()
            .sortedByDescending { Instant.parse(it.createdAt) }
            .map { row ->
                RiceMovement(
                    id = row.id,
                    kind = RiceMovementKind.valueOf(row.kind),
                    quantityGrams = row.quantityGrams,
                    note = row.note,
                    createdAt = displayTime(row.createdAt.toString()),
                )
            }
        val expenses = client.from("expenses").select().decodeList<ExpenseDto>()
            .sortedWith(
                compareByDescending<ExpenseDto> { it.spentOn }
                    .thenByDescending { Instant.parse(it.createdAt) },
            )
            .map { row ->
                ExpenseRecord(
                    id = row.id,
                    category = ExpenseCategory.valueOf(row.category),
                    amountMinor = row.amountMinor,
                    spentOn = row.spentOn,
                    description = row.description,
                    transactionReference = row.transactionReference,
                    createdAt = displayTime(row.createdAt.toString()),
                )
            }
        val expenseReversals = client.from("expense_reversals").select().decodeList<ExpenseReversalDto>()
            .sortedByDescending { Instant.parse(it.createdAt) }
            .map { row ->
                ExpenseReversal(row.expenseId, row.reason, displayTime(row.createdAt.toString()))
            }
        val cashBook = client.from("cash_book_entries").select().decodeList<CashBookEntryDto>()
            .sortedByDescending { Instant.parse(it.occurredAt) }
            .map { row ->
                CashBookEntry(
                    id = row.id,
                    sourceType = row.sourceType,
                    sourceId = row.sourceId,
                    occurredAt = displayTime(row.occurredAt.toString()),
                    direction = CashBookDirection.valueOf(row.direction),
                    amountMinor = row.amountMinor,
                    description = row.description,
                    memberId = row.memberId,
                    memberName = row.memberId?.let(memberNames::get),
                )
            }

        return OperationsSnapshot(
            members = profiles,
            meals = meals,
            mealReversals = mealReversals,
            riceMovements = riceMovements,
            expenses = expenses,
            expenseReversals = expenseReversals,
            cashBook = cashBook,
        )
    }

    override suspend fun recordMeal(
        memberId: String,
        mealDate: String,
        slot: MealSlot,
        quantityUnits: Int,
        note: String?,
        clientRequestId: String,
    ) {
        requireSession()
        client.postgrest.rpc(
            "record_meal_entry",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(memberId))
                put("target_date", JsonPrimitive(mealDate))
                put("target_slot", JsonPrimitive(slot.name))
                put("target_quantity_units", JsonPrimitive(quantityUnits))
                put("target_note", note?.let(::JsonPrimitive) ?: JsonNull)
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun reverseMeal(entryId: String, reason: String) {
        requireSession()
        client.postgrest.rpc(
            "reverse_meal_entry",
            buildJsonObject {
                put("target_entry_id", JsonPrimitive(entryId))
                put("target_reason", JsonPrimitive(reason))
            },
        )
    }

    override suspend fun recordRice(
        kind: RiceMovementKind,
        quantityGrams: Int,
        note: String,
        clientRequestId: String,
    ) {
        requireSession()
        client.postgrest.rpc(
            "record_rice_movement",
            buildJsonObject {
                put("target_kind", JsonPrimitive(kind.name))
                put("target_quantity_grams", JsonPrimitive(quantityGrams))
                put("target_note", JsonPrimitive(note))
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun recordExpense(
        category: ExpenseCategory,
        amountMinor: Long,
        spentOn: String,
        description: String,
        reference: String?,
        clientRequestId: String,
    ) {
        requireSession()
        client.postgrest.rpc(
            "record_expense",
            buildJsonObject {
                put("target_category", JsonPrimitive(category.name))
                put("target_amount_minor", JsonPrimitive(amountMinor))
                put("target_spent_on", JsonPrimitive(spentOn))
                put("target_description", JsonPrimitive(description))
                put("target_reference", reference?.let(::JsonPrimitive) ?: JsonNull)
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun reverseExpense(expenseId: String, reason: String) {
        requireSession()
        client.postgrest.rpc(
            "reverse_expense",
            buildJsonObject {
                put("target_expense_id", JsonPrimitive(expenseId))
                put("target_reason", JsonPrimitive(reason))
            },
        )
    }

    private fun displayTime(value: String): String = Instant.parse(value)
        .atZone(ZoneId.systemDefault())
        .format(displayFormatter)

    private fun requireSession() {
        check(client.auth.currentUserOrNull() != null) { "An authenticated session is required." }
    }
}
