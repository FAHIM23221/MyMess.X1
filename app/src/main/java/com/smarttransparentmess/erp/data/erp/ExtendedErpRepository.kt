package com.smarttransparentmess.erp.data.erp

import com.smarttransparentmess.erp.domain.erp.ErpAction
import com.smarttransparentmess.erp.domain.erp.ExtendedErpSnapshot
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface ExtendedErpRepository {
    suspend fun loadSnapshot(): ExtendedErpSnapshot
    suspend fun execute(action: ErpAction, fields: Map<String, String>, requestId: String)
}

class SupabaseExtendedErpRepository(
    private val client: SupabaseClient,
) : ExtendedErpRepository {
    override suspend fun loadSnapshot(): ExtendedErpSnapshot {
        requireSession()
        return client.postgrest.rpc("get_extended_erp_snapshot")
            .decodeSingle<ExtendedErpSnapshotDto>()
            .toDomain(Instant.now().toString())
    }

    override suspend fun execute(action: ErpAction, fields: Map<String, String>, requestId: String) {
        requireSession()
        when (action) {
            ErpAction.CREATE_FINE -> client.postgrest.rpc(
                "create_fine",
                buildJsonObject {
                    put("target_user_id", JsonPrimitive(fields.getValue("member_id")))
                    put("target_period_id", JsonPrimitive(fields.getValue("period_id")))
                    put("target_amount_minor", JsonPrimitive(fields.getValue("amount_minor").toLong()))
                    put("target_reason", JsonPrimitive(fields.getValue("reason")))
                    putNullable("target_due_date", fields["due_date"])
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.REVIEW_PAYMENT -> client.postgrest.rpc(
                "review_payment",
                buildJsonObject {
                    put("target_payment_id", JsonPrimitive(fields.getValue("payment_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.SET_MEAL_BILLING_POLICY -> client.postgrest.rpc(
                "set_meal_billing_policy",
                buildJsonObject {
                    put("target_price_per_half_meal_minor", JsonPrimitive(fields.getValue("price_per_half_meal_minor").toLong()))
                    put("target_enabled", JsonPrimitive(fields.getValue("enabled").toBooleanStrict()))
                },
            )
            ErpAction.GENERATE_MEAL_BILL -> client.postgrest.rpc(
                "generate_meal_bill",
                buildJsonObject {
                    put("target_period_id", JsonPrimitive(fields.getValue("period_id")))
                    putNullable("target_due_date", fields["due_date"])
                },
            )
        }
    }

    private fun requireSession() {
        check(client.auth.currentUserOrNull() != null) { "An authenticated session is required." }
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value.isNullOrBlank()) put(key, JsonNull) else put(key, JsonPrimitive(value))
}
