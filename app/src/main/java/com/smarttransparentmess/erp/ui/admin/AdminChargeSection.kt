package com.smarttransparentmess.erp.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.presentation.AdminFinanceNotice
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.presentation.AdminFinanceViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.labelRes

@Composable
fun AdminChargeSection(
    snapshot: AdminFinanceSnapshot,
    state: AdminFinanceUiState,
    viewModel: AdminFinanceViewModel,
    memberId: String,
    onMemberSelected: (String) -> Unit,
    periodId: String,
    onPeriodSelected: (String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(ChargeCategory.SEAT_RENT.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var categoryFilterName by rememberSaveable { mutableStateOf("") }
    val category = ChargeCategory.valueOf(categoryName)

    var adjustmentAmount by rememberSaveable { mutableStateOf("") }
    var adjustmentReason by rememberSaveable { mutableStateOf("") }
    var adjustmentKindName by rememberSaveable { mutableStateOf(AdjustmentKind.OPENING_DUE.name) }
    val adjustmentKind = AdjustmentKind.valueOf(adjustmentKindName)
    val categoryFilter = categoryFilterName.takeIf { it.isNotBlank() }?.let(ChargeCategory::valueOf)
    val filteredCharges = snapshot.charges.filter { charge ->
        val query = searchQuery.trim().lowercase()
        (query.isEmpty() || charge.memberName.lowercase().contains(query) ||
            charge.periodLabel.lowercase().contains(query) ||
            charge.description.lowercase().contains(query)) &&
            (categoryFilter == null || charge.category == categoryFilter)
    }

    LaunchedEffect(state.notice) {
        when (state.notice) {
            AdminFinanceNotice.CHARGE_CREATED -> {
                amount = ""
                dueDate = ""
                description = ""
            }
            AdminFinanceNotice.ADJUSTMENT_CREATED -> {
                adjustmentAmount = ""
                adjustmentReason = ""
            }
            else -> Unit
        }
    }

    FormCard(title = stringResource(R.string.add_charge)) {
        MemberPeriodPicker(
            snapshot = snapshot,
            memberId = memberId,
            onMemberSelected = onMemberSelected,
            periodId = periodId,
            onPeriodSelected = onPeriodSelected,
            enabled = !state.isSubmitting,
        )
        SelectionField(
            label = stringResource(R.string.category_label),
            options = ChargeCategory.entries,
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
            value = dueDate,
            onValueChange = { dueDate = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.due_date_optional)) },
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
        PrimaryActionButton(
            text = stringResource(R.string.add_charge),
            onClick = {
                viewModel.createCharge(
                    memberId = memberId,
                    periodId = periodId,
                    category = category,
                    amountText = amount,
                    description = description,
                    dueDate = dueDate,
                )
            },
            enabled = !state.isSubmitting && memberId.isNotBlank() && periodId.isNotBlank(),
            loading = state.isSubmitting,
        )
    }

    FormCard(
        title = stringResource(R.string.add_adjustment),
        subtitle = stringResource(R.string.carry_forward_body),
    ) {
        MemberPeriodPicker(
            snapshot = snapshot,
            memberId = memberId,
            onMemberSelected = onMemberSelected,
            periodId = periodId,
            onPeriodSelected = onPeriodSelected,
            enabled = !state.isSubmitting,
        )
        SelectionField(
            label = stringResource(R.string.kind_label),
            options = AdjustmentKind.entries,
            selected = adjustmentKind,
            display = { stringResource(it.labelRes()) },
            onSelected = { adjustmentKindName = it.name },
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            value = adjustmentAmount,
            onValueChange = { adjustmentAmount = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.amount_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            value = adjustmentReason,
            onValueChange = { adjustmentReason = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.reason_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = !state.isSubmitting,
        )
        PrimaryActionButton(
            text = stringResource(R.string.add_adjustment),
            onClick = {
                viewModel.addAdjustment(
                    memberId = memberId,
                    periodId = periodId,
                    kind = adjustmentKind,
                    amountText = adjustmentAmount,
                    reason = adjustmentReason,
                )
            },
            enabled = !state.isSubmitting && memberId.isNotBlank() && periodId.isNotBlank(),
            loading = state.isSubmitting,
        )
    }

    Text(stringResource(R.string.charge_history), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text(stringResource(R.string.search_charges)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    SelectionField(
        label = stringResource(R.string.category_label),
        options = listOf<ChargeCategory?>(null) + ChargeCategory.entries,
        selected = categoryFilter,
        display = { option ->
            option?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.all_categories)
        },
        onSelected = { categoryFilterName = it?.name.orEmpty() },
        emptyText = stringResource(R.string.all_categories),
    )
    Text(
        stringResource(R.string.charge_count, filteredCharges.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (filteredCharges.isEmpty()) {
        Text(stringResource(R.string.no_charge_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        filteredCharges.forEach { charge ->
            FormCard(
                title = stringResource(charge.category.labelRes()),
                subtitle = "${charge.memberName} · ${charge.periodLabel}",
            ) {
                KeyValueRow(
                    stringResource(R.string.amount_label),
                    stringResource(R.string.bdt_amount, Money.format(charge.amountMinor)),
                )
                KeyValueRow(stringResource(R.string.description_label), charge.description)
                KeyValueRow(stringResource(R.string.due_date_optional), charge.dueDate ?: "—")
            }
        }
    }

    Text(stringResource(R.string.adjustment_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.adjustments.isEmpty()) {
        Text(stringResource(R.string.no_adjustment_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        snapshot.adjustments.forEach { adjustment ->
            FormCard(
                title = stringResource(adjustment.kind.labelRes()),
                subtitle = "${adjustment.memberName} · ${adjustment.periodLabel}",
            ) {
                KeyValueRow(
                    stringResource(R.string.amount_label),
                    stringResource(R.string.bdt_amount, Money.format(adjustment.amountMinor)),
                )
                KeyValueRow(stringResource(R.string.reason_label), adjustment.reason)
            }
        }
    }
}
