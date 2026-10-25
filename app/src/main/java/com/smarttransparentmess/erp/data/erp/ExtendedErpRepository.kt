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
            ErpAction.SET_MEAL_SCHEDULE -> client.postgrest.rpc(
                "set_meal_schedule",
                buildJsonObject {
                    put("target_user_id", JsonPrimitive(fields.getValue("member_id")))
                    put("target_start", JsonPrimitive(fields.getValue("starts_on")))
                    putNullable("target_end", fields["ends_on"])
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.RECORD_RICE_PURCHASE -> client.postgrest.rpc(
                "record_rice_purchase",
                buildJsonObject {
                    put("target_quantity_grams", JsonPrimitive(fields.getValue("quantity_grams").toInt()))
                    put("target_unit", JsonPrimitive(fields.getValue("unit")))
                    put("target_unit_price_minor", JsonPrimitive(fields.getValue("unit_price_minor").toLong()))
                    put("target_vendor", JsonPrimitive(fields.getValue("vendor")))
                    putNullable("target_receipt_reference", fields["receipt_reference"])
                    put("target_purchased_on", JsonPrimitive(fields.getValue("purchased_on")))
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.REVERSE_RICE_MOVEMENT -> client.postgrest.rpc(
                "reverse_rice_movement",
                buildJsonObject {
                    put("target_movement_id", JsonPrimitive(fields.getValue("movement_id")))
                    put("target_reason", JsonPrimitive(fields.getValue("reason")))
                },
            )
            ErpAction.UPDATE_EXPENSE_DETAILS -> client.postgrest.rpc(
                "update_expense_details",
                buildJsonObject {
                    put("target_expense_id", JsonPrimitive(fields.getValue("expense_id")))
                    putNullable("target_vendor", fields["vendor"])
                    putNullable("target_receipt_reference", fields["receipt_reference"])
                },
            )
            ErpAction.REVIEW_EXPENSE -> client.postgrest.rpc(
                "review_expense",
                buildJsonObject {
                    put("target_expense_id", JsonPrimitive(fields.getValue("expense_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.UPSERT_CASH_ACCOUNT -> client.postgrest.rpc(
                "upsert_cash_account",
                buildJsonObject {
                    put("target_name", JsonPrimitive(fields.getValue("name")))
                    put("target_kind", JsonPrimitive(fields.getValue("kind")))
                    put("target_opening_balance_minor", JsonPrimitive(fields.getValue("opening_balance_minor").toLong()))
                },
            )
            ErpAction.ASSIGN_CASH_ENTRY_ACCOUNT -> client.postgrest.rpc(
                "assign_cash_entry_account",
                buildJsonObject {
                    put("target_cash_book_entry_id", JsonPrimitive(fields.getValue("cash_book_entry_id")))
                    put("target_account_id", JsonPrimitive(fields.getValue("account_id")))
                },
            )
            ErpAction.CLOSE_CASH_PERIOD -> client.postgrest.rpc(
                "close_cash_period",
                buildJsonObject {
                    put("target_start", JsonPrimitive(fields.getValue("starts_on")))
                    put("target_end", JsonPrimitive(fields.getValue("ends_on")))
                    put("target_opening_balance_minor", JsonPrimitive(fields.getValue("opening_balance_minor").toLong()))
                    putNullable("target_note", fields["note"])
                },
            )
            ErpAction.UPDATE_MESS_SETTINGS -> client.postgrest.rpc(
                "update_mess_settings",
                buildJsonObject {
                    put("target_locale", JsonPrimitive(fields.getValue("locale")))
                    put("target_low_rice_threshold_grams", JsonPrimitive(fields.getValue("low_rice_threshold_grams").toInt()))
                    put("target_meal_reminders_enabled", JsonPrimitive(fields.getValue("meal_reminders_enabled").toBooleanStrict()))
                },
            )
            ErpAction.MARK_NOTIFICATION_READ -> client.postgrest.rpc(
                "mark_notification_read",
                buildJsonObject { put("target_notification_id", JsonPrimitive(fields.getValue("notification_id"))) },
            )
            ErpAction.CREATE_DUTY -> client.postgrest.rpc(
                "create_duty",
                buildJsonObject {
                    put("target_user_id", JsonPrimitive(fields.getValue("assigned_to")))
                    put("target_title", JsonPrimitive(fields.getValue("title")))
                    put("target_description", JsonPrimitive(fields.getValue("description")))
                    put("target_due_on", JsonPrimitive(fields.getValue("due_on")))
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.UPDATE_DUTY_STATUS -> client.postgrest.rpc(
                "update_duty_status",
                buildJsonObject {
                    put("target_duty_id", JsonPrimitive(fields.getValue("duty_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_note", fields["note"])
                },
            )
            ErpAction.REQUEST_DUTY_CHANGE -> client.postgrest.rpc(
                "request_duty_change",
                buildJsonObject {
                    put("target_duty_id", JsonPrimitive(fields.getValue("duty_id")))
                    put("target_kind", JsonPrimitive(fields.getValue("kind")))
                    putNullable("target_member_id", fields["target_member_id"])
                    put("target_reason", JsonPrimitive(fields.getValue("reason")))
                },
            )
            ErpAction.REVIEW_DUTY_REQUEST -> client.postgrest.rpc(
                "review_duty_request",
                buildJsonObject {
                    put("target_request_id", JsonPrimitive(fields.getValue("request_id")))
                    put("target_decision", JsonPrimitive(fields.getValue("decision")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.SCHEDULE_MANAGER_ROTATION -> client.postgrest.rpc(
                "schedule_manager_rotation",
                buildJsonObject {
                    put("target_manager_id", JsonPrimitive(fields.getValue("manager_id")))
                    put("target_start", JsonPrimitive(fields.getValue("starts_on")))
                    putNullable("target_end", fields["ends_on"])
                    putNullable("target_handover_note", fields["handover_note"])
                },
            )
            ErpAction.ACTIVATE_MANAGER_ROTATION -> client.postgrest.rpc(
                "activate_manager_rotation",
                buildJsonObject { put("target_rotation_id", JsonPrimitive(fields.getValue("rotation_id"))) },
            )
            ErpAction.SAVE_NOTICE -> client.postgrest.rpc(
                "save_notice",
                buildJsonObject {
                    put("target_title", JsonPrimitive(fields.getValue("title")))
                    put("target_body", JsonPrimitive(fields.getValue("body")))
                    put("target_audience", JsonPrimitive(fields.getValue("audience")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_publish_at", fields["publish_at"])
                    putNullable("target_expires_at", fields["expires_at"])
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.SET_NOTICE_STATUS -> client.postgrest.rpc(
                "set_notice_status",
                buildJsonObject {
                    put("target_notice_id", JsonPrimitive(fields.getValue("notice_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                },
            )
            ErpAction.PUBLISH_DUE_NOTICES -> client.postgrest.rpc("publish_due_notices")
            ErpAction.MARK_NOTICE_READ -> client.postgrest.rpc(
                "mark_notice_read",
                buildJsonObject { put("target_notice_id", JsonPrimitive(fields.getValue("notice_id"))) },
            )
            ErpAction.SEND_MESSAGE -> client.postgrest.rpc(
                "send_message",
                buildJsonObject {
                    put("target_kind", JsonPrimitive(fields.getValue("kind")))
                    putNullable("target_recipient_id", fields["recipient_id"])
                    put("target_body", JsonPrimitive(fields.getValue("body")))
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.MARK_MESSAGE_READ -> client.postgrest.rpc(
                "mark_message_read",
                buildJsonObject { put("target_message_id", JsonPrimitive(fields.getValue("message_id"))) },
            )
            ErpAction.REPORT_MESSAGE -> client.postgrest.rpc(
                "report_message",
                buildJsonObject {
                    put("target_message_id", JsonPrimitive(fields.getValue("message_id")))
                    put("target_reason", JsonPrimitive(fields.getValue("reason")))
                },
            )
            ErpAction.REVIEW_MESSAGE_REPORT -> client.postgrest.rpc(
                "review_message_report",
                buildJsonObject {
                    put("target_report_id", JsonPrimitive(fields.getValue("report_id")))
                    put("target_decision", JsonPrimitive(fields.getValue("decision")))
                },
            )
            ErpAction.MODERATE_MESSAGE -> client.postgrest.rpc(
                "moderate_message",
                buildJsonObject {
                    put("target_message_id", JsonPrimitive(fields.getValue("message_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.CREATE_SUGGESTION -> client.postgrest.rpc(
                "create_suggestion",
                buildJsonObject {
                    put("target_body", JsonPrimitive(fields.getValue("body")))
                    put("client_request_id", JsonPrimitive(requestId))
                },
            )
            ErpAction.REVIEW_SUGGESTION -> client.postgrest.rpc(
                "review_suggestion",
                buildJsonObject {
                    put("target_suggestion_id", JsonPrimitive(fields.getValue("suggestion_id")))
                    put("target_decision", JsonPrimitive(fields.getValue("decision")))
                    putNullable("target_reason", fields["reason"])
                },
            )
            ErpAction.CREATE_COMPLAINT -> client.postgrest.rpc(
                "create_complaint",
                buildJsonObject {
                    put("target_subject", JsonPrimitive(fields.getValue("subject")))
                    put("target_body", JsonPrimitive(fields.getValue("body")))
                },
            )
            ErpAction.RESPOND_COMPLAINT -> client.postgrest.rpc(
                "respond_complaint",
                buildJsonObject {
                    put("target_complaint_id", JsonPrimitive(fields.getValue("complaint_id")))
                    put("target_status", JsonPrimitive(fields.getValue("status")))
                    put("target_response", JsonPrimitive(fields.getValue("response")))
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
