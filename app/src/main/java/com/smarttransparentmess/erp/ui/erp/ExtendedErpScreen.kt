package com.smarttransparentmess.erp.ui.erp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.erp.ErpAction
import com.smarttransparentmess.erp.domain.erp.ErpRecord
import com.smarttransparentmess.erp.domain.erp.ExtendedErpSnapshot
import com.smarttransparentmess.erp.domain.erp.ExtendedErpValidationError
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.presentation.ExtendedErpNotice
import com.smarttransparentmess.erp.presentation.ExtendedErpUiState
import com.smarttransparentmess.erp.presentation.ExtendedErpViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.LoadingContent
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.theme.Spacing

@Composable
fun ExtendedErpScreen(
    state: ExtendedErpUiState,
    viewModel: ExtendedErpViewModel,
    isAdmin: Boolean,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    var activeAction by remember { mutableStateOf<ErpAction?>(null) }
    var prefill by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val snapshot = state.snapshot

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.extended_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.extended_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.isLoading && !state.isSubmitting) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
            }
        }
        state.notice?.let { notice ->
            val message = when (notice) {
                ExtendedErpNotice.OPERATION_SUCCEEDED -> R.string.extended_operation_success
                ExtendedErpNotice.MEMBER_NOTIFICATION_SAVED -> R.string.extended_member_notification_saved
            }
            InfoCard(stringResource(R.string.extended_success_title), stringResource(message))
        }
        if (state.operationFailed) {
            InfoCard(stringResource(R.string.extended_error_title), stringResource(R.string.extended_operation_failed))
        }
        state.validationError?.let { error ->
            InfoCard(stringResource(R.string.extended_error_title), stringResource(error.messageRes()))
        }
        if (state.isLoading && snapshot != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        when {
            snapshot == null && state.isLoading -> LoadingContent(stringResource(R.string.extended_loading), Modifier.fillMaxWidth())
            snapshot == null -> InfoCard(stringResource(R.string.extended_title), stringResource(R.string.extended_no_data))
            else -> {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.extended_billing_tab)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.extended_operations_tab)) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.extended_duties_tab)) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(stringResource(R.string.extended_community_tab)) })
                }
                when (selectedTab) {
                    0 -> BillingWorkspace(
                        snapshot = snapshot,
                        isAdmin = isAdmin,
                        isSubmitting = state.isSubmitting,
                        onAction = { action, values ->
                            viewModel.clearFeedback()
                            prefill = values
                            activeAction = action
                        },
                    )
                    1 -> OperationsWorkspace(
                        snapshot = snapshot,
                        isAdmin = isAdmin,
                        currentUserId = currentUserId,
                        isSubmitting = state.isSubmitting,
                        onAction = { action, values ->
                            viewModel.clearFeedback()
                            prefill = values
                            activeAction = action
                        },
                    )
                    2 -> DutiesWorkspace(
                        snapshot = snapshot,
                        isAdmin = isAdmin,
                        currentUserId = currentUserId,
                        isSubmitting = state.isSubmitting,
                        onAction = { action, values ->
                            viewModel.clearFeedback()
                            prefill = values
                            activeAction = action
                        },
                    )
                    else -> CommunityWorkspace(
                        snapshot = snapshot,
                        isAdmin = isAdmin,
                        currentUserId = currentUserId,
                        isSubmitting = state.isSubmitting,
                        onAction = { action, values ->
                            viewModel.clearFeedback()
                            prefill = values
                            activeAction = action
                        },
                    )
                }
            }
        }
    }

    activeAction?.let { action ->
        if (snapshot != null) {
            ErpActionDialog(
                action = action,
                snapshot = snapshot,
                prefill = prefill,
                isAdmin = isAdmin,
                currentUserId = currentUserId,
                isSubmitting = state.isSubmitting,
                onDismiss = { activeAction = null },
                onSubmit = { values ->
                    viewModel.execute(action, values)
                    activeAction = null
                },
            )
        }
    }
}

@Composable
private fun BillingWorkspace(
    snapshot: ExtendedErpSnapshot,
    isAdmin: Boolean,
    isSubmitting: Boolean,
    onAction: (ErpAction, Map<String, String>) -> Unit,
) {
    if (isAdmin) {
        FormCard(title = stringResource(R.string.extended_billing_actions), subtitle = stringResource(R.string.extended_billing_actions_body)) {
            PrimaryActionButton(stringResource(R.string.extended_add_fine), { onAction(ErpAction.CREATE_FINE, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_review_payment), { onAction(ErpAction.REVIEW_PAYMENT, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_meal_policy), { onAction(ErpAction.SET_MEAL_BILLING_POLICY, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_generate_bill), { onAction(ErpAction.GENERATE_MEAL_BILL, emptyMap()) }, enabled = !isSubmitting)
        }
    }

    FormCard(title = stringResource(R.string.extended_meal_policy)) {
        val policy = snapshot.mealBillingPolicies.firstOrNull()
        if (policy == null) {
            Text(stringResource(R.string.extended_no_policy))
        } else {
            KeyValueRow(
                stringResource(R.string.extended_price_half_meal),
                stringResource(R.string.bdt_amount, Money.format(policy.long("price_per_half_meal_minor") ?: 0L)),
            )
            StatusPill(stringResource(if (policy.boolean("enabled")) R.string.extended_enabled else R.string.extended_disabled))
        }
    }

    FormCard(title = stringResource(R.string.extended_fines)) {
        if (snapshot.fines.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.fines.forEach { fine -> FineCard(fine) }
    }

    FormCard(title = stringResource(R.string.extended_review_payment)) {
        if (snapshot.paymentReviews.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.paymentReviews.forEach { payment ->
            PaymentReviewCard(
                payment = payment,
                isAdmin = isAdmin,
                enabled = !isSubmitting,
                onReview = { onAction(ErpAction.REVIEW_PAYMENT, mapOf("payment_id" to payment.id)) },
            )
        }
    }

    FormCard(title = stringResource(R.string.extended_bill_runs)) {
        if (snapshot.mealBillingRuns.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.mealBillingRuns.forEach { run ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XSmall)) {
                Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                    Text(run.text("period_label"), style = MaterialTheme.typography.titleSmall)
                    KeyValueRow(stringResource(R.string.extended_half_meal_units), run.text("total_half_meal_units"))
                    KeyValueRow(stringResource(R.string.extended_total_amount), stringResource(R.string.bdt_amount, Money.format(run.long("total_amount_minor") ?: 0L)))
                    Text(run.text("created_at").substringBefore('.'), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FineCard(fine: ErpRecord) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XSmall)) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Text(fine.text("member_name"), style = MaterialTheme.typography.titleSmall)
            KeyValueRow(stringResource(R.string.amount_label), stringResource(R.string.bdt_amount, Money.format(fine.long("amount_minor") ?: 0L)))
            KeyValueRow(stringResource(R.string.select_period), fine.text("period_label"))
            Text(fine.text("reason"), style = MaterialTheme.typography.bodyMedium)
            if (fine.text("due_date").isNotBlank()) KeyValueRow(stringResource(R.string.due_date_optional), fine.text("due_date"))
        }
    }
}

@Composable
private fun PaymentReviewCard(payment: ErpRecord, isAdmin: Boolean, enabled: Boolean, onReview: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XSmall)) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(payment.text("member_name"), style = MaterialTheme.typography.titleSmall)
                StatusPill(payment.status)
            }
            KeyValueRow(stringResource(R.string.amount_label), stringResource(R.string.bdt_amount, Money.format(payment.long("amount_minor") ?: 0L)))
            KeyValueRow(stringResource(R.string.select_period), payment.text("period_label"))
            KeyValueRow(stringResource(R.string.extended_payment_method), payment.text("payment_method"))
            if (payment.text("transaction_reference").isNotBlank()) KeyValueRow(stringResource(R.string.extended_reference), payment.text("transaction_reference"))
            if (payment.boolean("reversed")) Text(stringResource(R.string.payment_status_reversed), color = MaterialTheme.colorScheme.error)
            if (payment.text("reason").isNotBlank()) Text(payment.text("reason"), style = MaterialTheme.typography.bodySmall)
            if (isAdmin && !payment.boolean("reversed")) {
                OutlinedButton(onClick = onReview, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.extended_review_payment))
                }
            }
        }
    }
}

@Composable
private fun DutiesWorkspace(
    snapshot: ExtendedErpSnapshot,
    isAdmin: Boolean,
    currentUserId: String,
    isSubmitting: Boolean,
    onAction: (ErpAction, Map<String, String>) -> Unit,
) {
    FormCard(title = stringResource(R.string.extended_duties), subtitle = stringResource(R.string.extended_duties_body)) {
        if (isAdmin) PrimaryActionButton(stringResource(R.string.extended_create_duty), { onAction(ErpAction.CREATE_DUTY, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.duties.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.duties.forEach { record ->
            ErpRecordCard(record, listOf("title", "assignee_name", "due_on", "status", "description", "completion_note"))
            if (record.status == "ASSIGNED") {
                if (isAdmin || record.text("assigned_to") == currentUserId) {
                    OutlinedButton(onClick = { onAction(ErpAction.UPDATE_DUTY_STATUS, mapOf("duty_id" to record.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.extended_update_duty))
                    }
                }
                if (record.text("assigned_to") == currentUserId) {
                    OutlinedButton(onClick = { onAction(ErpAction.REQUEST_DUTY_CHANGE, mapOf("duty_id" to record.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.extended_request_duty_change))
                    }
                }
            }
        }
    }
    FormCard(title = stringResource(R.string.extended_duty_requests)) {
        if (isAdmin) PrimaryActionButton(stringResource(R.string.extended_review_duty_request), { onAction(ErpAction.REVIEW_DUTY_REQUEST, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.dutyRequests.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.dutyRequests.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("duty_title", "requester_name", "kind", "target_name", "reason", "status", "decision_reason"),
                actionLabel = if (isAdmin && record.status == "PENDING") stringResource(R.string.extended_review_duty_request) else null,
                onAction = { onAction(ErpAction.REVIEW_DUTY_REQUEST, mapOf("request_id" to record.id)) },
            )
        }
    }
    FormCard(title = stringResource(R.string.extended_rotations)) {
        if (isAdmin) PrimaryActionButton(stringResource(R.string.extended_schedule_rotation), { onAction(ErpAction.SCHEDULE_MANAGER_ROTATION, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.managerRotations.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.managerRotations.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("manager_name", "starts_on", "ends_on", "status", "handover_note"),
                actionLabel = if (isAdmin && record.status == "SCHEDULED") stringResource(R.string.extended_activate_rotation) else null,
                onAction = { onAction(ErpAction.ACTIVATE_MANAGER_ROTATION, mapOf("rotation_id" to record.id)) },
            )
        }
    }
    FormCard(title = stringResource(R.string.extended_notices), subtitle = stringResource(R.string.extended_notices_body)) {
        if (isAdmin) {
            PrimaryActionButton(stringResource(R.string.extended_create_notice), { onAction(ErpAction.SAVE_NOTICE, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_publish_due), { onAction(ErpAction.PUBLISH_DUE_NOTICES, emptyMap()) }, enabled = !isSubmitting)
        }
        if (snapshot.notices.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.notices.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("title", "audience", "status", "body", "publish_at", "expires_at", "read_count"),
                actionLabel = if (isAdmin) stringResource(R.string.extended_notice_status) else if (!record.boolean("current_user_read")) stringResource(R.string.extended_mark_read) else null,
                onAction = {
                    if (isAdmin) onAction(ErpAction.SET_NOTICE_STATUS, mapOf("notice_id" to record.id))
                    else onAction(ErpAction.MARK_NOTICE_READ, mapOf("notice_id" to record.id))
                },
            )
        }
    }
}

@Composable
private fun CommunityWorkspace(
    snapshot: ExtendedErpSnapshot,
    isAdmin: Boolean,
    currentUserId: String,
    isSubmitting: Boolean,
    onAction: (ErpAction, Map<String, String>) -> Unit,
) {
    val isMainAdmin = snapshot.members.firstOrNull { it.id == currentUserId }?.text("role") == "MAIN_ADMIN"
    FormCard(title = stringResource(R.string.extended_messages), subtitle = stringResource(R.string.extended_messages_body)) {
        PrimaryActionButton(stringResource(R.string.extended_new_message), { onAction(ErpAction.SEND_MESSAGE, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.messages.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.messages.forEach { record ->
            ErpRecordCard(record, listOf("sender_name", "recipient_name", "kind", "body", "status", "created_at", "current_user_read"))
            if (!record.boolean("current_user_read") && record.text("sender_id") != currentUserId) {
                OutlinedButton(onClick = { onAction(ErpAction.MARK_MESSAGE_READ, mapOf("message_id" to record.id)) }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.extended_mark_read))
                }
            }
            OutlinedButton(onClick = { onAction(ErpAction.REPORT_MESSAGE, mapOf("message_id" to record.id)) }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.extended_report_message))
            }
            if (isMainAdmin) {
                OutlinedButton(onClick = {
                    onAction(ErpAction.MODERATE_MESSAGE, mapOf("message_id" to record.id, "status" to if (record.status == "HIDDEN") "VISIBLE" else "HIDDEN"))
                }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (record.status == "HIDDEN") R.string.extended_restore_message else R.string.extended_hide_message))
                }
            }
        }
    }
    FormCard(title = stringResource(R.string.extended_suggestions), subtitle = stringResource(R.string.extended_suggestions_body)) {
        PrimaryActionButton(stringResource(R.string.extended_new_suggestion), { onAction(ErpAction.CREATE_SUGGESTION, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.suggestions.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.suggestions.forEach { record ->
            ErpRecordCard(record, listOf("body", "status", "moderation_reason", "author_name", "created_at"))
            if (isMainAdmin && record.status == "PENDING") {
                OutlinedButton(onClick = { onAction(ErpAction.REVIEW_SUGGESTION, mapOf("suggestion_id" to record.id)) }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.extended_review_suggestion))
                }
            }
        }
    }
    FormCard(title = stringResource(R.string.extended_complaints), subtitle = stringResource(R.string.extended_complaints_body)) {
        PrimaryActionButton(stringResource(R.string.extended_new_complaint), { onAction(ErpAction.CREATE_COMPLAINT, emptyMap()) }, enabled = !isSubmitting)
        if (snapshot.complaints.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.complaints.forEach { record ->
            ErpRecordCard(record, listOf("subject", "author_name", "status", "body", "response", "created_at"))
            if (isAdmin && record.status !in setOf("RESOLVED", "REJECTED")) {
                OutlinedButton(onClick = { onAction(ErpAction.RESPOND_COMPLAINT, mapOf("complaint_id" to record.id)) }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.extended_respond_complaint))
                }
            }
        }
    }
    if (isAdmin) {
        FormCard(title = stringResource(R.string.extended_message_reports)) {
            if (snapshot.messageReports.isEmpty()) Text(stringResource(R.string.extended_no_records))
            snapshot.messageReports.forEach { record ->
                ErpRecordCard(record, listOf("reporter_name", "reason", "status", "message_id", "created_at"))
                if (record.status == "OPEN") {
                    OutlinedButton(onClick = { onAction(ErpAction.REVIEW_MESSAGE_REPORT, mapOf("report_id" to record.id)) }, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.extended_review_report))
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationsWorkspace(
    snapshot: ExtendedErpSnapshot,
    isAdmin: Boolean,
    currentUserId: String,
    isSubmitting: Boolean,
    onAction: (ErpAction, Map<String, String>) -> Unit,
) {
    FormCard(title = stringResource(R.string.extended_meal_schedules), subtitle = stringResource(R.string.extended_meal_schedules_body)) {
        PrimaryActionButton(
            stringResource(R.string.extended_set_schedule),
            { onAction(ErpAction.SET_MEAL_SCHEDULE, if (isAdmin) emptyMap() else mapOf("member_id" to currentUserId)) },
            enabled = !isSubmitting,
        )
        if (snapshot.mealSchedules.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.mealSchedules.forEach { record ->
            ErpRecordCard(record, listOf("member_name", "starts_on", "ends_on", "status", "reason"))
        }
    }

    FormCard(title = stringResource(R.string.extended_rice_workspace)) {
        if (isAdmin) {
            PrimaryActionButton(stringResource(R.string.extended_record_purchase), { onAction(ErpAction.RECORD_RICE_PURCHASE, emptyMap()) }, enabled = !isSubmitting)
        }
        snapshot.riceStockAlerts.forEach { record ->
            ErpRecordCard(record, listOf("status", "stock_grams", "threshold_grams", "created_at", "resolved_at"))
        }
        if (snapshot.ricePurchases.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.ricePurchases.forEach { record ->
            ErpRecordCard(record, listOf("vendor", "quantity_grams", "purchase_unit", "unit_price_minor", "total_amount_minor", "receipt_reference", "purchased_on"))
        }
        snapshot.riceMovements.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("kind", "quantity_grams", "note", "created_at", "reversed"),
                actionLabel = if (isAdmin && !record.boolean("reversed")) stringResource(R.string.extended_reverse_rice) else null,
                onAction = { onAction(ErpAction.REVERSE_RICE_MOVEMENT, mapOf("movement_id" to record.id)) },
            )
        }
    }

    FormCard(title = stringResource(R.string.extended_expense_workspace)) {
        if (isAdmin) {
            PrimaryActionButton(stringResource(R.string.extended_update_expense), { onAction(ErpAction.UPDATE_EXPENSE_DETAILS, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_review_expense), { onAction(ErpAction.REVIEW_EXPENSE, emptyMap()) }, enabled = !isSubmitting)
        }
        if (snapshot.expenses.isEmpty()) Text(stringResource(R.string.extended_no_records))
        snapshot.expenses.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("description", "category", "amount_minor", "spent_on", "vendor", "approval_status", "approval_reason"),
                actionLabel = if (isAdmin) stringResource(R.string.extended_review_expense) else null,
                onAction = { onAction(ErpAction.REVIEW_EXPENSE, mapOf("expense_id" to record.id)) },
            )
        }
    }

    FormCard(title = stringResource(R.string.extended_cash_workspace)) {
        if (isAdmin) {
            PrimaryActionButton(stringResource(R.string.extended_add_cash_account), { onAction(ErpAction.UPSERT_CASH_ACCOUNT, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_assign_cash), { onAction(ErpAction.ASSIGN_CASH_ENTRY_ACCOUNT, emptyMap()) }, enabled = !isSubmitting)
            PrimaryActionButton(stringResource(R.string.extended_close_cash), { onAction(ErpAction.CLOSE_CASH_PERIOD, emptyMap()) }, enabled = !isSubmitting)
        }
        snapshot.cashAccounts.forEach { record -> ErpRecordCard(record, listOf("name", "kind", "opening_balance_minor", "active")) }
        snapshot.cashEntries.take(30).forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("description", "source_type", "direction", "amount_minor", "business_date", "account_name"),
                actionLabel = if (isAdmin) stringResource(R.string.extended_assign_cash) else null,
                onAction = { onAction(ErpAction.ASSIGN_CASH_ENTRY_ACCOUNT, mapOf("cash_book_entry_id" to record.id)) },
            )
        }
        snapshot.cashPeriodLocks.forEach { record -> ErpRecordCard(record, listOf("starts_on", "ends_on", "opening_balance_minor", "closing_balance_minor", "note")) }
    }

    FormCard(title = stringResource(R.string.extended_settings_notifications)) {
        if (isAdmin) PrimaryActionButton(stringResource(R.string.extended_update_settings), { onAction(ErpAction.UPDATE_MESS_SETTINGS, emptyMap()) }, enabled = !isSubmitting)
        snapshot.messSettings.forEach { record -> ErpRecordCard(record, listOf("locale", "timezone", "low_rice_threshold_grams", "meal_reminders_enabled")) }
        if (snapshot.notifications.isEmpty()) Text(stringResource(R.string.extended_no_notifications))
        snapshot.notifications.forEach { record ->
            ErpRecordCard(
                record = record,
                fields = listOf("title", "kind", "body", "created_at", "read_at"),
                actionLabel = if (record.text("read_at").isBlank()) stringResource(R.string.extended_mark_read) else null,
                onAction = { onAction(ErpAction.MARK_NOTIFICATION_READ, mapOf("notification_id" to record.id)) },
            )
        }
        if (isAdmin) {
            Text(stringResource(R.string.extended_send_member_notification), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.extended_send_member_notification_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { onAction(ErpAction.SEND_MEMBER_NOTIFICATION, emptyMap()) },
                enabled = !isSubmitting,
            ) { Text(stringResource(R.string.extended_send_member_notification)) }
            Text(stringResource(R.string.extended_notification_history), style = MaterialTheme.typography.titleSmall)
            if (snapshot.notificationBatches.isEmpty()) {
                Text(
                    stringResource(R.string.extended_no_notification_batches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            snapshot.notificationBatches.take(5).forEach { batch ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
                    ) {
                        Text(batch.text("title"), style = MaterialTheme.typography.titleSmall)
                        Text(batch.text("body"), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(
                                R.string.extended_notification_batch_meta,
                                batch.text("audience"),
                                batch.long("recipient_count") ?: 0L,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val target = batch.text("target_member_name")
                        if (target.isNotBlank()) {
                            Text(
                                stringResource(R.string.extended_notification_batch_target, target),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.extended_notification_batch_actor_time,
                                batch.text("actor_name"),
                                batch.text("created_at").substringBefore('.'),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErpRecordCard(
    record: ErpRecord,
    fields: List<String>,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XSmall)) {
        Column(modifier = Modifier.padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            fields.forEachIndexed { index, key ->
                val value = record.text(key)
                if (value.isNotBlank()) {
                    if (index == 0) Text(value, style = MaterialTheme.typography.titleSmall)
                    else KeyValueRow(erpFieldLabel(key), erpFieldValue(key, value))
                }
            }
            if (fields.none { record.text(it).isNotBlank() }) Text(record.id, style = MaterialTheme.typography.bodySmall)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun erpFieldLabel(key: String): String = when (key) {
    "member_name" -> stringResource(R.string.select_member)
    "amount_minor", "total_amount_minor", "unit_price_minor", "opening_balance_minor", "closing_balance_minor" -> stringResource(R.string.amount_label)
    "reason", "approval_reason" -> stringResource(R.string.reason_label)
    "status", "approval_status" -> stringResource(R.string.status_label)
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun erpFieldValue(key: String, value: String): String =
    if (key.endsWith("amount_minor") || key.endsWith("balance_minor") || key == "unit_price_minor") {
        stringResource(R.string.bdt_amount, Money.format(value.toLongOrNull() ?: 0L))
    } else value

private data class ErpOption(val id: String, val label: String)
private data class ErpField(
    val key: String,
    val label: String,
    val options: List<ErpOption> = emptyList(),
    val singleLine: Boolean = true,
)
private data class ErpActionSpec(val title: String, val fields: List<ErpField>)

@Composable
private fun ErpActionDialog(
    action: ErpAction,
    snapshot: ExtendedErpSnapshot,
    prefill: Map<String, String>,
    isAdmin: Boolean,
    currentUserId: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
) {
    val spec = actionSpec(action, snapshot, isAdmin, currentUserId)
    var values by remember(action, prefill) { mutableStateOf(prefill) }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(spec.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                spec.fields.forEach { field ->
                    if (action == ErpAction.SEND_MEMBER_NOTIFICATION &&
                        field.key == "member_id" && values["audience"] != "MEMBER"
                    ) {
                        return@forEach
                    }
                    if (field.options.isNotEmpty()) {
                        val selected = field.options.firstOrNull { it.id == values[field.key] }
                        SelectionField(
                            label = field.label,
                            options = field.options,
                            selected = selected,
                            display = { it.label },
                            onSelected = { values = values + (field.key to it.id) },
                            emptyText = field.label,
                        )
                    } else {
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { values = values + (field.key to it) },
                            label = { Text(field.label) },
                            singleLine = field.singleLine,
                            minLines = if (field.singleLine) 1 else 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(values) }, enabled = !isSubmitting) {
                Text(stringResource(R.string.extended_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun actionSpec(action: ErpAction, snapshot: ExtendedErpSnapshot, isAdmin: Boolean, currentUserId: String): ErpActionSpec {
    val allMemberOptions = snapshot.members
        .filter { it.text("account_status") == "ACTIVE" && it.text("departed_at").isBlank() }
        .map { ErpOption(it.id, "${it.text("full_name")} (${it.text("member_id")})") }
    val memberOptions = allMemberOptions.filter { isAdmin || it.id == currentUserId }
    val openPeriods = snapshot.periods.filter { it.status == "OPEN" }
        .map { ErpOption(it.id, it.text("label")) }
    val paymentOptions = snapshot.paymentReviews.filter { !it.boolean("reversed") }
        .map { ErpOption(it.id, "${it.text("member_name")} · ${it.text("period_label")} · ${it.status}") }
    val riceMovementOptions = snapshot.riceMovements.filter { !it.boolean("reversed") }
        .map { ErpOption(it.id, "${it.text("kind")} · ${it.text("quantity_grams")} g · ${it.text("created_at").substringBefore('.')}") }
    val expenseOptions = snapshot.expenses.map { ErpOption(it.id, "${it.text("description")} · ${it.text("spent_on")}") }
    val cashEntryOptions = snapshot.cashEntries.map { ErpOption(it.id, "${it.text("description")} · ${it.text("business_date")}") }
    val cashAccountOptions = snapshot.cashAccounts.filter { it.boolean("active") }.map { ErpOption(it.id, "${it.text("name")} · ${it.text("kind")}") }
    val dutyOptions = snapshot.duties.filter { it.status == "ASSIGNED" && (isAdmin || it.text("assigned_to") == currentUserId) }
        .map { ErpOption(it.id, "${it.text("title")} · ${it.text("due_on")}") }
    val requestOptions = snapshot.dutyRequests.filter { it.status == "PENDING" }
        .map { ErpOption(it.id, "${it.text("duty_title")} · ${it.text("requester_name")}") }
    val managerOptions = snapshot.members.filter { it.text("account_status") == "ACTIVE" && it.text("role") in setOf("ADMIN", "MAIN_ADMIN") }
        .map { ErpOption(it.id, it.text("full_name")) }
    val rotationOptions = snapshot.managerRotations.filter { it.status == "SCHEDULED" }
        .map { ErpOption(it.id, "${it.text("manager_name")} · ${it.text("starts_on")}") }
    val noticeOptions = snapshot.notices.map { ErpOption(it.id, "${it.text("title")} · ${it.status}") }
    val messageOptions = snapshot.messages.map { ErpOption(it.id, "${it.text("kind")} · ${it.text("sender_name")} · ${it.text("created_at").substringBefore('.')}") }
    val messageRecipientOptions = allMemberOptions.filter { option ->
        option.id != currentUserId && (isAdmin || snapshot.members.firstOrNull { it.id == option.id }?.text("role") in setOf("ADMIN", "MAIN_ADMIN"))
    }
    val suggestionOptions = snapshot.suggestions.filter { it.status == "PENDING" }.map { ErpOption(it.id, it.text("body").take(80)) }
    val complaintOptions = snapshot.complaints.map { ErpOption(it.id, "${it.text("subject")} · ${it.status}") }
    val reportOptions = snapshot.messageReports.filter { it.status == "OPEN" }.map { ErpOption(it.id, "${it.text("reporter_name")} · ${it.text("reason").take(60)}") }
    return when (action) {
        ErpAction.CREATE_FINE -> ErpActionSpec(
            stringResource(R.string.extended_add_fine),
            listOf(
                ErpField("member_id", stringResource(R.string.select_member), memberOptions),
                ErpField("period_id", stringResource(R.string.select_period), openPeriods),
                ErpField("amount_minor", stringResource(R.string.extended_amount_minor)),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
                ErpField("due_date", stringResource(R.string.due_date_optional)),
            ),
        )
        ErpAction.REVIEW_PAYMENT -> ErpActionSpec(
            stringResource(R.string.extended_review_payment),
            listOf(
                ErpField("payment_id", stringResource(R.string.extended_select_payment), paymentOptions),
                ErpField("status", stringResource(R.string.extended_review_status), listOf(
                    ErpOption("VERIFIED", stringResource(R.string.extended_verified)),
                    ErpOption("REJECTED", stringResource(R.string.extended_rejected)),
                )),
                ErpField("reason", stringResource(R.string.extended_review_reason), singleLine = false),
            ),
        )
        ErpAction.SET_MEAL_BILLING_POLICY -> ErpActionSpec(
            stringResource(R.string.extended_meal_policy),
            listOf(
                ErpField("price_per_half_meal_minor", stringResource(R.string.extended_price_minor)),
                ErpField("enabled", stringResource(R.string.extended_review_status), listOf(
                    ErpOption("true", stringResource(R.string.extended_enabled)),
                    ErpOption("false", stringResource(R.string.extended_disabled)),
                )),
            ),
        )
        ErpAction.GENERATE_MEAL_BILL -> ErpActionSpec(
            stringResource(R.string.extended_generate_bill),
            listOf(
                ErpField("period_id", stringResource(R.string.select_period), openPeriods),
                ErpField("due_date", stringResource(R.string.due_date_optional)),
            ),
        )
        ErpAction.SET_MEAL_SCHEDULE -> ErpActionSpec(
            stringResource(R.string.extended_set_schedule),
            listOf(
                ErpField("member_id", stringResource(R.string.select_member), memberOptions),
                ErpField("starts_on", stringResource(R.string.period_start)),
                ErpField("ends_on", stringResource(R.string.period_end)),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("ON", "ON"), ErpOption("OFF", "OFF"))),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.RECORD_RICE_PURCHASE -> ErpActionSpec(
            stringResource(R.string.extended_record_purchase),
            listOf(
                ErpField("quantity_grams", stringResource(R.string.extended_quantity_grams)),
                ErpField("unit", stringResource(R.string.extended_purchase_unit), listOf(ErpOption("KG", "KG"), ErpOption("GRAM", "GRAM"))),
                ErpField("unit_price_minor", stringResource(R.string.extended_price_minor)),
                ErpField("vendor", stringResource(R.string.extended_vendor)),
                ErpField("receipt_reference", stringResource(R.string.extended_reference)),
                ErpField("purchased_on", stringResource(R.string.date_label)),
            ),
        )
        ErpAction.REVERSE_RICE_MOVEMENT -> ErpActionSpec(
            stringResource(R.string.extended_reverse_rice),
            listOf(
                ErpField("movement_id", stringResource(R.string.extended_rice_movement), riceMovementOptions),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.UPDATE_EXPENSE_DETAILS -> ErpActionSpec(
            stringResource(R.string.extended_update_expense),
            listOf(
                ErpField("expense_id", stringResource(R.string.extended_expense), expenseOptions),
                ErpField("vendor", stringResource(R.string.extended_vendor)),
                ErpField("receipt_reference", stringResource(R.string.extended_reference)),
            ),
        )
        ErpAction.REVIEW_EXPENSE -> ErpActionSpec(
            stringResource(R.string.extended_review_expense),
            listOf(
                ErpField("expense_id", stringResource(R.string.extended_expense), expenseOptions),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("APPROVED", stringResource(R.string.extended_verified)), ErpOption("REJECTED", stringResource(R.string.extended_rejected)))),
                ErpField("reason", stringResource(R.string.extended_review_reason), singleLine = false),
            ),
        )
        ErpAction.UPSERT_CASH_ACCOUNT -> ErpActionSpec(
            stringResource(R.string.extended_add_cash_account),
            listOf(
                ErpField("name", stringResource(R.string.extended_account_name)),
                ErpField("kind", stringResource(R.string.extended_account_kind), listOf(ErpOption("CASH", "CASH"), ErpOption("BANK", "BANK"), ErpOption("MOBILE_WALLET", "MOBILE_WALLET"))),
                ErpField("opening_balance_minor", stringResource(R.string.extended_opening_balance)),
            ),
        )
        ErpAction.ASSIGN_CASH_ENTRY_ACCOUNT -> ErpActionSpec(
            stringResource(R.string.extended_assign_cash),
            listOf(
                ErpField("cash_book_entry_id", stringResource(R.string.extended_cash_entry), cashEntryOptions),
                ErpField("account_id", stringResource(R.string.extended_cash_account), cashAccountOptions),
            ),
        )
        ErpAction.CLOSE_CASH_PERIOD -> ErpActionSpec(
            stringResource(R.string.extended_close_cash),
            listOf(
                ErpField("starts_on", stringResource(R.string.period_start)),
                ErpField("ends_on", stringResource(R.string.period_end)),
                ErpField("opening_balance_minor", stringResource(R.string.extended_opening_balance)),
                ErpField("note", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.UPDATE_MESS_SETTINGS -> ErpActionSpec(
            stringResource(R.string.extended_update_settings),
            listOf(
                ErpField("locale", stringResource(R.string.extended_locale), listOf(ErpOption("bn", "বাংলা"), ErpOption("en", "English"))),
                ErpField("low_rice_threshold_grams", stringResource(R.string.extended_rice_threshold)),
                ErpField("meal_reminders_enabled", stringResource(R.string.extended_meal_reminders), listOf(ErpOption("true", stringResource(R.string.extended_enabled)), ErpOption("false", stringResource(R.string.extended_disabled)))),
            ),
        )
        ErpAction.MARK_NOTIFICATION_READ -> ErpActionSpec(
            stringResource(R.string.extended_mark_read),
            listOf(ErpField("notification_id", stringResource(R.string.extended_notification_id))),
        )
        ErpAction.SEND_MEMBER_NOTIFICATION -> ErpActionSpec(
            stringResource(R.string.extended_send_member_notification),
            listOf(
                ErpField(
                    "audience",
                    stringResource(R.string.extended_notification_audience),
                    listOf(
                        ErpOption("MEMBER", stringResource(R.string.extended_notification_audience_member)),
                        ErpOption("ALL", stringResource(R.string.extended_notification_audience_all)),
                        ErpOption("ADMINS", stringResource(R.string.extended_notification_audience_admins)),
                    ),
                ),
                ErpField("member_id", stringResource(R.string.select_member), allMemberOptions),
                ErpField("title", stringResource(R.string.extended_notification_title)),
                ErpField("body", stringResource(R.string.extended_notification_body), singleLine = false),
            ),
        )
        ErpAction.CREATE_DUTY -> ErpActionSpec(
            stringResource(R.string.extended_create_duty),
            listOf(
                ErpField("assigned_to", stringResource(R.string.select_member), memberOptions),
                ErpField("title", stringResource(R.string.extended_duty_title)),
                ErpField("description", stringResource(R.string.description_label), singleLine = false),
                ErpField("due_on", stringResource(R.string.date_label)),
            ),
        )
        ErpAction.UPDATE_DUTY_STATUS -> ErpActionSpec(
            stringResource(R.string.extended_update_duty),
            listOf(
                ErpField("duty_id", stringResource(R.string.extended_duty), dutyOptions),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("COMPLETED", "COMPLETED"), ErpOption("MISSED", "MISSED"), ErpOption("CANCELLED", "CANCELLED"))),
                ErpField("note", stringResource(R.string.description_label), singleLine = false),
            ),
        )
        ErpAction.REQUEST_DUTY_CHANGE -> ErpActionSpec(
            stringResource(R.string.extended_request_duty_change),
            listOf(
                ErpField("duty_id", stringResource(R.string.extended_duty), dutyOptions),
                ErpField("kind", stringResource(R.string.extended_request_kind), listOf(ErpOption("CANNOT_DO", "CANNOT_DO"), ErpOption("SWAP", "SWAP"))),
                ErpField("target_member_id", stringResource(R.string.extended_swap_target), allMemberOptions),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.REVIEW_DUTY_REQUEST -> ErpActionSpec(
            stringResource(R.string.extended_review_duty_request),
            listOf(
                ErpField("request_id", stringResource(R.string.extended_duty_request), requestOptions),
                ErpField("decision", stringResource(R.string.extended_review_status), listOf(ErpOption("APPROVED", stringResource(R.string.extended_verified)), ErpOption("REJECTED", stringResource(R.string.extended_rejected)))),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.SCHEDULE_MANAGER_ROTATION -> ErpActionSpec(
            stringResource(R.string.extended_schedule_rotation),
            listOf(
                ErpField("manager_id", stringResource(R.string.extended_manager), managerOptions),
                ErpField("starts_on", stringResource(R.string.period_start)),
                ErpField("ends_on", stringResource(R.string.period_end)),
                ErpField("handover_note", stringResource(R.string.extended_handover), singleLine = false),
            ),
        )
        ErpAction.ACTIVATE_MANAGER_ROTATION -> ErpActionSpec(
            stringResource(R.string.extended_activate_rotation),
            listOf(ErpField("rotation_id", stringResource(R.string.extended_rotation), rotationOptions)),
        )
        ErpAction.SAVE_NOTICE -> ErpActionSpec(
            stringResource(R.string.extended_create_notice),
            listOf(
                ErpField("title", stringResource(R.string.extended_notice_title)),
                ErpField("body", stringResource(R.string.extended_notice_body), singleLine = false),
                ErpField("audience", stringResource(R.string.extended_audience), listOf(ErpOption("ALL", "ALL"), ErpOption("MEMBERS", "MEMBERS"), ErpOption("ADMINS", "ADMINS"))),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("DRAFT", "DRAFT"), ErpOption("SCHEDULED", "SCHEDULED"), ErpOption("PUBLISHED", "PUBLISHED"))),
                ErpField("publish_at", stringResource(R.string.extended_publish_at)),
                ErpField("expires_at", stringResource(R.string.extended_expires_at)),
            ),
        )
        ErpAction.SET_NOTICE_STATUS -> ErpActionSpec(
            stringResource(R.string.extended_notice_status),
            listOf(
                ErpField("notice_id", stringResource(R.string.extended_notice), noticeOptions),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("PUBLISHED", "PUBLISHED"), ErpOption("CANCELLED", "CANCELLED"), ErpOption("ARCHIVED", "ARCHIVED"))),
            ),
        )
        ErpAction.PUBLISH_DUE_NOTICES -> ErpActionSpec(stringResource(R.string.extended_publish_due), emptyList())
        ErpAction.MARK_NOTICE_READ -> ErpActionSpec(
            stringResource(R.string.extended_mark_read),
            listOf(ErpField("notice_id", stringResource(R.string.extended_notice), noticeOptions)),
        )
        ErpAction.SEND_MESSAGE -> ErpActionSpec(
            stringResource(R.string.extended_new_message),
            listOf(
                ErpField("kind", stringResource(R.string.extended_message_kind), listOf(ErpOption("GROUP", stringResource(R.string.extended_group_message)), ErpOption("PRIVATE", stringResource(R.string.extended_private_message)))),
                ErpField("recipient_id", stringResource(R.string.extended_recipient), messageRecipientOptions),
                ErpField("body", stringResource(R.string.extended_message_body), singleLine = false),
            ),
        )
        ErpAction.MARK_MESSAGE_READ -> ErpActionSpec(
            stringResource(R.string.extended_mark_read),
            listOf(ErpField("message_id", stringResource(R.string.extended_message), messageOptions)),
        )
        ErpAction.REPORT_MESSAGE -> ErpActionSpec(
            stringResource(R.string.extended_report_message),
            listOf(
                ErpField("message_id", stringResource(R.string.extended_message), messageOptions),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.REVIEW_MESSAGE_REPORT -> ErpActionSpec(
            stringResource(R.string.extended_review_report),
            listOf(
                ErpField("report_id", stringResource(R.string.extended_message_report), reportOptions),
                ErpField("decision", stringResource(R.string.extended_review_status), listOf(ErpOption("REVIEWED", stringResource(R.string.extended_verified)), ErpOption("DISMISSED", stringResource(R.string.extended_dismissed)))),
            ),
        )
        ErpAction.MODERATE_MESSAGE -> ErpActionSpec(
            stringResource(R.string.extended_moderate_message),
            listOf(
                ErpField("message_id", stringResource(R.string.extended_message), messageOptions),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("VISIBLE", stringResource(R.string.extended_visible)), ErpOption("HIDDEN", stringResource(R.string.extended_hidden)))),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.CREATE_SUGGESTION -> ErpActionSpec(
            stringResource(R.string.extended_new_suggestion),
            listOf(ErpField("body", stringResource(R.string.extended_suggestion_body), singleLine = false)),
        )
        ErpAction.REVIEW_SUGGESTION -> ErpActionSpec(
            stringResource(R.string.extended_review_suggestion),
            listOf(
                ErpField("suggestion_id", stringResource(R.string.extended_suggestion), suggestionOptions),
                ErpField("decision", stringResource(R.string.extended_review_status), listOf(ErpOption("PUBLISHED", stringResource(R.string.extended_published)), ErpOption("REJECTED", stringResource(R.string.extended_rejected)))),
                ErpField("reason", stringResource(R.string.reason_label), singleLine = false),
            ),
        )
        ErpAction.CREATE_COMPLAINT -> ErpActionSpec(
            stringResource(R.string.extended_new_complaint),
            listOf(
                ErpField("subject", stringResource(R.string.extended_complaint_subject)),
                ErpField("body", stringResource(R.string.extended_complaint_body), singleLine = false),
            ),
        )
        ErpAction.RESPOND_COMPLAINT -> ErpActionSpec(
            stringResource(R.string.extended_respond_complaint),
            listOf(
                ErpField("complaint_id", stringResource(R.string.extended_complaint), complaintOptions),
                ErpField("status", stringResource(R.string.status_label), listOf(ErpOption("IN_PROGRESS", "IN_PROGRESS"), ErpOption("RESOLVED", "RESOLVED"), ErpOption("REJECTED", stringResource(R.string.extended_rejected)))),
                ErpField("response", stringResource(R.string.extended_response), singleLine = false),
            ),
        )
    }
}

private fun ExtendedErpValidationError.messageRes(): Int = when (this) {
    ExtendedErpValidationError.REQUIRED -> R.string.extended_validation_required
    ExtendedErpValidationError.AMOUNT -> R.string.validation_amount
    ExtendedErpValidationError.DATE -> R.string.validation_date
    ExtendedErpValidationError.REASON -> R.string.validation_reason
    ExtendedErpValidationError.SELECTION -> R.string.extended_validation_selection
}
