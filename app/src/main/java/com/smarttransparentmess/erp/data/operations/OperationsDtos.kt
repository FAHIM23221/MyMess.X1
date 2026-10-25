package com.smarttransparentmess.erp.data.operations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MealEntryDto(
    val id: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("meal_date") val mealDate: String,
    @SerialName("meal_slot") val slot: String,
    @SerialName("quantity_units") val quantityUnits: Int,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MealReversalDto(
    @SerialName("meal_entry_id") val mealEntryId: String,
    val reason: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RiceMovementDto(
    val id: String,
    val kind: String,
    @SerialName("quantity_grams") val quantityGrams: Int,
    val note: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseDto(
    val id: String,
    val category: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("spent_on") val spentOn: String,
    val description: String,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ExpenseReversalDto(
    @SerialName("expense_id") val expenseId: String,
    val reason: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CashBookEntryDto(
    val id: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("occurred_at") val occurredAt: String,
    val direction: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val description: String,
    @SerialName("member_id") val memberId: String? = null,
)
