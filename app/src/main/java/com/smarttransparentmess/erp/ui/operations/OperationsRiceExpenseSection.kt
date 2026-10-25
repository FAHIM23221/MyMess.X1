package com.smarttransparentmess.erp.ui.operations

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.operations.ExpenseCategory
import com.smarttransparentmess.erp.domain.operations.ExpenseRecord
import com.smarttransparentmess.erp.domain.operations.OperationsCalculator
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.domain.operations.RiceMovementKind
import com.smarttransparentmess.erp.presentation.OperationsNotice
import com.smarttransparentmess.erp.presentation.OperationsUiState
import com.smarttransparentmess.erp.presentation.OperationsViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.labelRes
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun OperationsRiceSection(
    snapshot: OperationsSnapshot,
    state: OperationsUiState,
    viewModel: OperationsViewModel,
    canManage: Boolean,
) {
    var kindName by rememberSaveable { mutableStateOf(RiceMovementKind.STOCK_IN.name) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val kind = RiceMovementKind.valueOf(kindName)
    val stock = remember(snapshot) { OperationsCalculator.summarize(snapshot).riceStockGrams }

    LaunchedEffect(state.notice) {
        if (state.notice == OperationsNotice.RICE_RECORDED) {
            quantity = ""
            note = ""
        }
    }

    FormCard(title = stringResource(R.string.operations_rice_stock)) {
        KeyValueRow(
            stringResource(R.string.operations_rice_stock),
            stringResource(R.string.rice_grams_value, stock),
        )
    }

    if (canManage) {
        FormCard(title = stringResource(R.string.record_rice_movement)) {
            SelectionField(
                label = stringResource(R.string.rice_movement_kind),
                options = RiceMovementKind.entries,
                selected = kind,
                display = { stringResource(it.labelRes()) },
                onSelected = { kindName = it.name },
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.rice_quantity_grams)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.rice_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !state.isSubmitting,
            )
            PrimaryActionButton(
                text = stringResource(R.string.record_rice_movement),
                onClick = { viewModel.recordRice(kind, quantity, note) },
                enabled = !state.isSubmitting,
                loading = state.isSubmitting,
            )
        }
    }

    Text(stringResource(R.string.rice_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.riceMovements.isEmpty()) {
        Text(stringResource(R.string.no_rice_movements), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        snapshot.riceMovements.forEach { movement ->
            FormCard(
                title = stringResource(movement.kind.labelRes()),
                subtitle = movement.createdAt,
            ) {
                KeyValueRow(
                    stringResource(R.string.rice_quantity_grams),
                    stringResource(R.string.rice_grams_value, movement.quantityGrams.toLong()),
                )
                Text(movement.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OperationsExpenseSection(
    snapshot: OperationsSnapshot,
    state: OperationsUiState,
    viewModel: OperationsViewModel,
    canManage: Boolean,
) {
    var categoryName by rememberSaveable { mutableStateOf(ExpenseCategory.GROCERY.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var spentOn by rememberSaveable {
        mutableStateOf(LocalDate.now(ZoneId.of("Asia/Dhaka")).toString())
    }
    var description by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var reversalTarget by remember { mutableStateOf<ExpenseRecord?>(null) }
    var reversalReason by rememberSaveable { mutableStateOf("") }
    val category = ExpenseCategory.valueOf(categoryName)

    LaunchedEffect(state.notice) {
        when (state.notice) {
            OperationsNotice.EXPENSE_RECORDED -> {
                amount = ""
                description = ""
                reference = ""
            }
            OperationsNotice.EXPENSE_REVERSED -> {
                reversalTarget = null
                reversalReason = ""
            }
            else -> Unit
        }
    }

    if (canManage) {
        FormCard(title = stringResource(R.string.record_expense)) {
            SelectionField(
                label = stringResource(R.string.category_label),
                options = ExpenseCategory.entries,
                selected = category,
                display = { stringResource(it.labelRes()) },
                onSelected = { categoryName = it.name },
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.amount_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = spentOn,
                onValueChange = { spentOn = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.date_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.reference_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitting,
            )
            PrimaryActionButton(
                text = stringResource(R.string.record_expense),
                onClick = {
                    viewModel.recordExpense(category, amount, spentOn, description, reference)
                },
                enabled = !state.isSubmitting,
                loading = state.isSubmitting,
            )
        }
    }

    Text(stringResource(R.string.expense_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.expenses.isEmpty()) {
        Text(stringResource(R.string.no_expenses), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        snapshot.expenses.forEach { expense ->
            val reversal = snapshot.expenseReversals.firstOrNull { it.expenseId == expense.id }
            FormCard(
                title = stringResource(expense.category.labelRes()),
                subtitle = expense.spentOn,
            ) {
                KeyValueRow(
                    stringResource(R.string.amount_label),
                    stringResource(R.string.bdt_amount, Money.format(expense.amountMinor)),
                )
                Text(expense.description, style = MaterialTheme.typography.bodyMedium)
                expense.transactionReference?.let {
                    KeyValueRow(stringResource(R.string.reference_optional), it)
                }
                StatusPill(
                    text = stringResource(
                        if (reversal == null) R.string.expense_status_recorded else R.string.payment_status_reversed,
                    ),
                )
                reversal?.let { Text(it.reason, style = MaterialTheme.typography.bodySmall) }
                if (canManage && reversal == null) {
                    OutlinedButton(
                        onClick = { reversalTarget = expense },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reverse_expense)) }
                }
            }
        }
    }

    reversalTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) reversalTarget = null },
            title = { Text(stringResource(R.string.reverse_expense)) },
            text = {
                OutlinedTextField(
                    value = reversalReason,
                    onValueChange = { reversalReason = it; viewModel.clearFeedback() },
                    label = { Text(stringResource(R.string.reason_label)) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !state.isSubmitting,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.reverseExpense(target.id, reversalReason) },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { reversalTarget = null },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
