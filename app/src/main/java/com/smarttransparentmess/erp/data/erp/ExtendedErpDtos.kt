package com.smarttransparentmess.erp.data.erp

import com.smarttransparentmess.erp.domain.erp.ErpRecord
import com.smarttransparentmess.erp.domain.erp.ExtendedErpSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ExtendedErpSnapshotDto(
    val members: List<JsonObject> = emptyList(),
    val periods: List<JsonObject> = emptyList(),
    val fines: List<JsonObject> = emptyList(),
    @SerialName("payment_reviews") val paymentReviews: List<JsonObject> = emptyList(),
    @SerialName("meal_billing_policies") val mealBillingPolicies: List<JsonObject> = emptyList(),
    @SerialName("meal_billing_runs") val mealBillingRuns: List<JsonObject> = emptyList(),
    @SerialName("meal_schedules") val mealSchedules: List<JsonObject> = emptyList(),
    @SerialName("rice_purchases") val ricePurchases: List<JsonObject> = emptyList(),
    @SerialName("rice_movements") val riceMovements: List<JsonObject> = emptyList(),
    @SerialName("rice_movement_reversals") val riceMovementReversals: List<JsonObject> = emptyList(),
    val expenses: List<JsonObject> = emptyList(),
    @SerialName("mess_settings") val messSettings: List<JsonObject> = emptyList(),
    val notifications: List<JsonObject> = emptyList(),
    @SerialName("notification_batches") val notificationBatches: List<JsonObject> = emptyList(),
    @SerialName("rice_stock_alerts") val riceStockAlerts: List<JsonObject> = emptyList(),
    @SerialName("cash_accounts") val cashAccounts: List<JsonObject> = emptyList(),
    @SerialName("cash_entries") val cashEntries: List<JsonObject> = emptyList(),
    @SerialName("cash_period_locks") val cashPeriodLocks: List<JsonObject> = emptyList(),
    val duties: List<JsonObject> = emptyList(),
    @SerialName("duty_requests") val dutyRequests: List<JsonObject> = emptyList(),
    @SerialName("manager_rotations") val managerRotations: List<JsonObject> = emptyList(),
    val notices: List<JsonObject> = emptyList(),
    val messages: List<JsonObject> = emptyList(),
    val suggestions: List<JsonObject> = emptyList(),
    val complaints: List<JsonObject> = emptyList(),
    @SerialName("message_reports") val messageReports: List<JsonObject> = emptyList(),
) {
    fun toDomain(loadedAt: String): ExtendedErpSnapshot = ExtendedErpSnapshot(
        members = members.map(JsonObject::toRecord),
        periods = periods.map(JsonObject::toRecord),
        fines = fines.map(JsonObject::toRecord),
        paymentReviews = paymentReviews.map(JsonObject::toRecord),
        mealBillingPolicies = mealBillingPolicies.map(JsonObject::toRecord),
        mealBillingRuns = mealBillingRuns.map(JsonObject::toRecord),
        mealSchedules = mealSchedules.map(JsonObject::toRecord),
        ricePurchases = ricePurchases.map(JsonObject::toRecord),
        riceMovements = riceMovements.map(JsonObject::toRecord),
        riceMovementReversals = riceMovementReversals.map(JsonObject::toRecord),
        expenses = expenses.map(JsonObject::toRecord),
        messSettings = messSettings.map(JsonObject::toRecord),
        notifications = notifications.map(JsonObject::toRecord),
        notificationBatches = notificationBatches.map(JsonObject::toRecord),
        riceStockAlerts = riceStockAlerts.map(JsonObject::toRecord),
        cashAccounts = cashAccounts.map(JsonObject::toRecord),
        cashEntries = cashEntries.map(JsonObject::toRecord),
        cashPeriodLocks = cashPeriodLocks.map(JsonObject::toRecord),
        duties = duties.map(JsonObject::toRecord),
        dutyRequests = dutyRequests.map(JsonObject::toRecord),
        managerRotations = managerRotations.map(JsonObject::toRecord),
        notices = notices.map(JsonObject::toRecord),
        messages = messages.map(JsonObject::toRecord),
        suggestions = suggestions.map(JsonObject::toRecord),
        complaints = complaints.map(JsonObject::toRecord),
        messageReports = messageReports.map(JsonObject::toRecord),
        loadedAt = loadedAt,
    )
}

private fun JsonObject.toRecord(): ErpRecord {
    val projected = entries.mapNotNull { (key, value) ->
        if (value is JsonNull) return@mapNotNull null
        val text = when (value) {
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
        key to text
    }.toMap()
    return ErpRecord(id = projected["id"].orEmpty(), values = projected)
}
