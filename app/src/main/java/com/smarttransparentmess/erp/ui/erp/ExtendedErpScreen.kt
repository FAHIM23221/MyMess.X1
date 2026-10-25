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
        state.notice?.let {
            InfoCard(stringResource(R.string.extended_success_title), stringResource(R.string.extended_operation_success))
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
                    else -> OperationsWorkspace(
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
    val memberOptions = snapshot.members
        .filter { it.text("account_status") == "ACTIVE" && it.text("departed_at").isBlank() && (isAdmin || it.id == currentUserId) }
        .map { ErpOption(it.id, "${it.text("full_name")} (${it.text("member_id")})") }
    val openPeriods = snapshot.periods.filter { it.status == "OPEN" }
        .map { ErpOption(it.id, it.text("label")) }
    val paymentOptions = snapshot.paymentReviews.filter { !it.boolean("reversed") }
        .map { ErpOption(it.id, "${it.text("member_name")} · ${it.text("period_label")} · ${it.status}") }
    val riceMovementOptions = snapshot.riceMovements.filter { !it.boolean("reversed") }
        .map { ErpOption(it.id, "${it.text("kind")} · ${it.text("quantity_grams")} g · ${it.text("created_at").substringBefore('.')}") }
    val expenseOptions = snapshot.expenses.map { ErpOption(it.id, "${it.text("description")} · ${it.text("spent_on")}") }
    val cashEntryOptions = snapshot.cashEntries.map { ErpOption(it.id, "${it.text("description")} · ${it.text("business_date")}") }
    val cashAccountOptions = snapshot.cashAccounts.filter { it.boolean("active") }.map { ErpOption(it.id, "${it.text("name")} · ${it.text("kind")}") }
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
    }
}

private fun ExtendedErpValidationError.messageRes(): Int = when (this) {
    ExtendedErpValidationError.REQUIRED -> R.string.extended_validation_required
    ExtendedErpValidationError.AMOUNT -> R.string.validation_amount
    ExtendedErpValidationError.DATE -> R.string.validation_date
    ExtendedErpValidationError.REASON -> R.string.validation_reason
    ExtendedErpValidationError.SELECTION -> R.string.extended_validation_selection
}
