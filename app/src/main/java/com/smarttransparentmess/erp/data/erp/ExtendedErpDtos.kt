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
) {
    fun toDomain(loadedAt: String): ExtendedErpSnapshot = ExtendedErpSnapshot(
        members = members.map(JsonObject::toRecord),
        periods = periods.map(JsonObject::toRecord),
        fines = fines.map(JsonObject::toRecord),
        paymentReviews = paymentReviews.map(JsonObject::toRecord),
        mealBillingPolicies = mealBillingPolicies.map(JsonObject::toRecord),
        mealBillingRuns = mealBillingRuns.map(JsonObject::toRecord),
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
