package com.smarttransparentmess.erp.ui.operations

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.KeyboardOptions
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.operations.MealEntry
import com.smarttransparentmess.erp.domain.operations.MealSlot
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.domain.operations.formatMealUnits
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
fun OperationsMealSection(
    snapshot: OperationsSnapshot,
    state: OperationsUiState,
    viewModel: OperationsViewModel,
    canManage: Boolean,
) {
    val activeMembers = snapshot.members.filter { it.accountStatus == AccountStatus.ACTIVE }
    var selectedMemberId by rememberSaveable { mutableStateOf("") }
    var mealDate by rememberSaveable {
        mutableStateOf(LocalDate.now(ZoneId.of("Asia/Dhaka")).toString())
    }
    var slotName by rememberSaveable { mutableStateOf(MealSlot.BREAKFAST.name) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var note by rememberSaveable { mutableStateOf("") }
    var reversalTarget by remember { mutableStateOf<MealEntry?>(null) }
    var reversalReason by rememberSaveable { mutableStateOf("") }
    val slot = MealSlot.valueOf(slotName)

    LaunchedEffect(activeMembers) {
        if (selectedMemberId !in activeMembers.map { it.userId }) {
            selectedMemberId = activeMembers.firstOrNull()?.userId.orEmpty()
        }
    }
    LaunchedEffect(state.notice) {
        when (state.notice) {
            OperationsNotice.MEAL_RECORDED -> {
                quantity = "1"
                note = ""
            }
            OperationsNotice.MEAL_REVERSED -> {
                reversalTarget = null
                reversalReason = ""
            }
            else -> Unit
        }
    }

    if (canManage) {
        FormCard(
            title = stringResource(R.string.record_meal),
            subtitle = stringResource(R.string.operations_no_auto_charge),
        ) {
            SelectionField(
                label = stringResource(R.string.select_member),
                options = activeMembers,
                selected = activeMembers.firstOrNull { it.userId == selectedMemberId },
                display = { member -> member.memberId?.let { "${member.fullName} · $it" } ?: member.fullName },
                onSelected = { selectedMemberId = it.userId },
                enabled = !state.isSubmitting,
                emptyText = stringResource(R.string.no_active_members),
            )
            OutlinedTextField(
                value = mealDate,
                onValueChange = { mealDate = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.date_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitting,
            )
            SelectionField(
                label = stringResource(R.string.meal_slot),
                options = MealSlot.entries,
                selected = slot,
                display = { stringResource(it.labelRes()) },
                onSelected = { slotName = it.name },
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.meal_quantity)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.isSubmitting,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; viewModel.clearFeedback() },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = !state.isSubmitting,
            )
            PrimaryActionButton(
                text = stringResource(R.string.record_meal),
                onClick = {
                    viewModel.recordMeal(selectedMemberId, mealDate, slot, quantity, note)
                },
                enabled = !state.isSubmitting && selectedMemberId.isNotBlank(),
                loading = state.isSubmitting,
            )
        }
    }

    Text(stringResource(R.string.meal_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.meals.isEmpty()) {
        Text(
            stringResource(R.string.no_meals),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        snapshot.meals.forEach { meal ->
            val reversal = snapshot.mealReversals.firstOrNull { it.mealEntryId == meal.id }
            FormCard(
                title = "${meal.memberName} · ${meal.mealDate}",
                subtitle = stringResource(meal.slot.labelRes()),
            ) {
                KeyValueRow(
                    stringResource(R.string.meal_quantity),
                    formatMealUnits(meal.quantityUnits.toLong()),
                )
                meal.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                StatusPill(
                    text = stringResource(
                        if (reversal == null) R.string.meal_status_recorded else R.string.payment_status_reversed,
                    ),
                )
                reversal?.let {
                    Text(it.reason, style = MaterialTheme.typography.bodySmall)
                }
                if (canManage && reversal == null) {
                    OutlinedButton(
                        onClick = { reversalTarget = meal },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reverse_meal)) }
                }
            }
        }
    }

    reversalTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) reversalTarget = null },
            title = { Text(stringResource(R.string.reverse_meal)) },
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
                    onClick = { viewModel.reverseMeal(target.id, reversalReason) },
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
