package com.smarttransparentmess.erp.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdminFinanceCalculator
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.members.ManagedLifecycleStatus
import com.smarttransparentmess.erp.domain.members.ManagedMember
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.operations.formatMealUnits
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.presentation.MemberManagementUiState
import com.smarttransparentmess.erp.presentation.MemberManagementViewModel
import com.smarttransparentmess.erp.presentation.OperationsUiState
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.StatusPill

private enum class MemberAction {
    SUSPEND,
    REACTIVATE,
    DEPART,
    MAKE_ADMIN,
    MAKE_MEMBER,
    TRANSFER_MAIN_ADMIN,
}

@Composable
fun MemberDetailSection(
    member: ManagedMember,
    currentUserId: String,
    currentRole: AppRole,
    state: MemberManagementUiState,
    financeState: AdminFinanceUiState,
    operationsState: OperationsUiState,
    viewModel: MemberManagementViewModel,
    onClose: () -> Unit,
) {
    var editing by rememberSaveable(member.userId) { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<MemberAction?>(null) }
    var actionReason by remember(pendingAction) { mutableStateOf("") }
    val isSelf = member.userId == currentUserId
    val canEdit = !member.isDeparted &&
        (currentRole == AppRole.MAIN_ADMIN || member.effectiveRole == AppRole.MEMBER)
    val canChangeLifecycle = !isSelf && member.effectiveRole != AppRole.MAIN_ADMIN &&
        (currentRole == AppRole.MAIN_ADMIN || member.effectiveRole == AppRole.MEMBER)
    val canChangeRole = currentRole == AppRole.MAIN_ADMIN && !isSelf &&
        member.effectiveRole != AppRole.MAIN_ADMIN && !member.isDeparted &&
        member.accountStatus == com.smarttransparentmess.erp.domain.model.AccountStatus.ACTIVE

    FormCard(title = stringResource(R.string.members_details_title)) {
        Text(member.fullName, style = MaterialTheme.typography.titleLarge)
        StatusPill(stringResource(member.lifecycleStatus.titleRes()))
        KeyValueRow(stringResource(R.string.email_label), member.email)
        KeyValueRow(stringResource(R.string.phone_label), member.phoneNumber.ifBlank { stringResource(R.string.not_provided) })
        KeyValueRow(stringResource(R.string.member_id_label), member.memberId ?: stringResource(R.string.not_assigned))
        KeyValueRow(stringResource(R.string.role_label), stringResource(member.effectiveRole.titleRes()))
        KeyValueRow(stringResource(R.string.join_date_label), member.joinDate ?: stringResource(R.string.not_provided))
        KeyValueRow(
            stringResource(R.string.manager_status_label),
            stringResource(if (member.isCurrentManager) R.string.manager_yes else R.string.manager_no),
        )
        member.departureReason?.let {
            KeyValueRow(stringResource(R.string.members_departure_reason), it)
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.close))
        }
    }

    if (editing) {
        EditMemberForm(
            member = member,
            state = state,
            viewModel = viewModel,
            onClose = { editing = false },
        )
    } else if (canEdit) {
        PrimaryActionButton(
            text = stringResource(R.string.members_edit_member),
            onClick = { editing = true },
            enabled = !state.isSubmitting,
        )
    }

    if (canChangeLifecycle) {
        FormCard(title = stringResource(R.string.members_lifecycle_title)) {
            Text(
                stringResource(R.string.members_lifecycle_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (member.lifecycleStatus != ManagedLifecycleStatus.ACTIVE) {
                PrimaryActionButton(
                    text = stringResource(R.string.members_reactivate),
                    onClick = { pendingAction = MemberAction.REACTIVATE },
                    enabled = !state.isSubmitting,
                )
            }
            if (member.lifecycleStatus != ManagedLifecycleStatus.SUSPENDED &&
                member.lifecycleStatus != ManagedLifecycleStatus.DEPARTED
            ) {
                PrimaryActionButton(
                    text = stringResource(R.string.members_suspend),
                    onClick = { pendingAction = MemberAction.SUSPEND },
                    enabled = !state.isSubmitting,
                )
            }
            if (member.lifecycleStatus != ManagedLifecycleStatus.DEPARTED) {
                PrimaryActionButton(
                    text = stringResource(R.string.members_mark_departed),
                    onClick = { pendingAction = MemberAction.DEPART },
                    enabled = !state.isSubmitting,
                )
            }
        }
    }

    if (canChangeRole) {
        FormCard(title = stringResource(R.string.members_role_management)) {
            Text(
                stringResource(R.string.members_role_management_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (member.effectiveRole == AppRole.ADMIN) {
                PrimaryActionButton(
                    text = stringResource(R.string.members_make_member),
                    onClick = { pendingAction = MemberAction.MAKE_MEMBER },
                    enabled = !state.isSubmitting,
                )
            } else {
                PrimaryActionButton(
                    text = stringResource(R.string.members_make_admin),
                    onClick = { pendingAction = MemberAction.MAKE_ADMIN },
                    enabled = !state.isSubmitting,
                )
            }
            PrimaryActionButton(
                text = stringResource(R.string.members_transfer_main_admin),
                onClick = { pendingAction = MemberAction.TRANSFER_MAIN_ADMIN },
                enabled = !state.isSubmitting,
            )
        }
    }

    MemberFinanceHistory(member, financeState)
    MemberMealHistory(member, operationsState)

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(action.titleRes())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(com.smarttransparentmess.erp.ui.theme.Spacing.Medium)) {
                    Text(stringResource(action.bodyRes()))
                    OutlinedTextField(
                        value = actionReason,
                        onValueChange = { actionReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(stringResource(R.string.reason_label)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            MemberAction.SUSPEND -> viewModel.setLifecycle(member.userId, "SUSPENDED", actionReason)
                            MemberAction.REACTIVATE -> viewModel.setLifecycle(member.userId, "ACTIVE", actionReason)
                            MemberAction.DEPART -> viewModel.setLifecycle(member.userId, "DEPARTED", actionReason)
                            MemberAction.MAKE_ADMIN -> viewModel.setRole(member.userId, AppRole.ADMIN, actionReason)
                            MemberAction.MAKE_MEMBER -> viewModel.setRole(member.userId, AppRole.MEMBER, actionReason)
                            MemberAction.TRANSFER_MAIN_ADMIN -> viewModel.transferMainAdmin(member.userId, actionReason)
                        }
                        pendingAction = null
                    },
                    enabled = actionReason.isNotBlank() && !state.isSubmitting,
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EditMemberForm(
    member: ManagedMember,
    state: MemberManagementUiState,
    viewModel: MemberManagementViewModel,
    onClose: () -> Unit,
) {
    var fullName by rememberSaveable(member.userId) { mutableStateOf(member.fullName) }
    var phone by rememberSaveable(member.userId) { mutableStateOf(member.phoneNumber) }
    var memberId by rememberSaveable(member.userId) { mutableStateOf(member.memberId.orEmpty()) }
    var joinDate by rememberSaveable(member.userId) { mutableStateOf(member.joinDate.orEmpty()) }

    FormCard(title = stringResource(R.string.members_edit_member)) {
        OutlinedTextField(
            value = fullName, onValueChange = { fullName = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.full_name_label)) },
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.phone_label)) },
        )
        OutlinedTextField(
            value = memberId, onValueChange = { memberId = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.member_id_label)) },
        )
        OutlinedTextField(
            value = joinDate, onValueChange = { joinDate = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.members_join_date_input)) },
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_profile),
            onClick = { viewModel.updateMember(member.userId, fullName, phone, memberId, joinDate) },
            enabled = !state.isSubmitting,
            loading = state.isSubmitting,
        )
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun MemberFinanceHistory(member: ManagedMember, state: AdminFinanceUiState) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.isLoading -> InfoCard(
            title = stringResource(R.string.members_finance_history),
            body = stringResource(R.string.members_history_loading),
        )
        snapshot == null -> InfoCard(
            title = stringResource(R.string.members_finance_history),
            body = stringResource(R.string.members_no_finance_history),
        )
        else -> {
            val balances = remember(snapshot, member.userId) {
                snapshot.periods.mapNotNull { period ->
                    AdminFinanceCalculator.calculate(snapshot, period.id).memberBalances
                        .firstOrNull { it.memberId == member.userId }
                        ?.let { period.label to it.finance }
                }
            }
            val charges = snapshot.charges.filter { it.memberId == member.userId }
            val payments = snapshot.payments.filter { it.memberId == member.userId }
            val adjustments = snapshot.adjustments.filter { it.memberId == member.userId }
            FormCard(title = stringResource(R.string.members_finance_history)) {
                if (balances.isEmpty()) {
                    Text(stringResource(R.string.members_no_finance_history))
                } else {
                    balances.forEach { (label, finance) ->
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        KeyValueRow(
                            stringResource(R.string.finance_due),
                            stringResource(R.string.bdt_amount, Money.format(finance.outstandingDueMinor)),
                        )
                        KeyValueRow(
                            stringResource(R.string.finance_advance),
                            stringResource(R.string.bdt_amount, Money.format(finance.advanceCreditMinor)),
                        )
                    }
                }
                KeyValueRow(stringResource(R.string.finance_charges), charges.size.toString())
                KeyValueRow(stringResource(R.string.payment_history), payments.size.toString())
                KeyValueRow(stringResource(R.string.adjustment_history), adjustments.size.toString())
            }
            if (charges.isNotEmpty()) {
                FormCard(title = stringResource(R.string.charge_history)) {
                    charges.take(30).forEach { charge ->
                        Text(
                            "${charge.periodLabel} • ${charge.category.name} • ${Money.format(charge.amountMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(charge.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (payments.isNotEmpty()) {
                FormCard(title = stringResource(R.string.payment_history)) {
                    payments.take(30).forEach { receipt ->
                        val payment = receipt.payment
                        Text(
                            "${payment.receiptNumber} • ${Money.format(payment.amountMinor)} • ${payment.method.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${receipt.periodLabel} • ${payment.paidAt} • " +
                                stringResource(if (payment.isValid) R.string.finance_valid else R.string.finance_reversed),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        payment.transactionReference?.let {
                            Text(stringResource(R.string.finance_transaction_ref, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberMealHistory(member: ManagedMember, state: OperationsUiState) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.isLoading -> InfoCard(
            title = stringResource(R.string.members_meal_history),
            body = stringResource(R.string.members_history_loading),
        )
        snapshot == null -> InfoCard(
            title = stringResource(R.string.members_meal_history),
            body = stringResource(R.string.members_no_meal_history),
        )
        else -> {
            val meals = snapshot.meals.filter { it.memberId == member.userId }
            val reversedIds = snapshot.mealReversals.mapTo(hashSetOf()) { it.mealEntryId }
            val cashEntries = snapshot.cashBook.filter { it.memberId == member.userId }
            FormCard(title = stringResource(R.string.members_meal_history)) {
                if (meals.isEmpty()) {
                    Text(stringResource(R.string.members_no_meal_history))
                } else {
                    meals.take(40).forEach { meal ->
                        Text(
                            "${meal.mealDate} • ${meal.slot.name} • ${formatMealUnits(meal.quantityUnits.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (meal.id in reversedIds) {
                            Text(stringResource(R.string.finance_reversed), color = MaterialTheme.colorScheme.error)
                        }
                        meal.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (cashEntries.isNotEmpty()) {
                FormCard(title = stringResource(R.string.members_cash_history)) {
                    cashEntries.take(30).forEach { entry ->
                        Text(
                            "${entry.occurredAt} • ${entry.direction.name} • ${Money.format(entry.amountMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(entry.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun ManagedLifecycleStatus.titleRes(): Int = when (this) {
    ManagedLifecycleStatus.ACTIVE -> R.string.active_status
    ManagedLifecycleStatus.PENDING -> R.string.pending_status
    ManagedLifecycleStatus.SUSPENDED -> R.string.suspended_status
    ManagedLifecycleStatus.DEPARTED -> R.string.departed_status
}

private fun AppRole.titleRes(): Int = when (this) {
    AppRole.MEMBER -> R.string.member_role
    AppRole.ADMIN -> R.string.admin_role
    AppRole.MAIN_ADMIN -> R.string.main_admin_role
}

private fun MemberAction.titleRes(): Int = when (this) {
    MemberAction.SUSPEND -> R.string.members_suspend
    MemberAction.REACTIVATE -> R.string.members_reactivate
    MemberAction.DEPART -> R.string.members_mark_departed
    MemberAction.MAKE_ADMIN -> R.string.members_make_admin
    MemberAction.MAKE_MEMBER -> R.string.members_make_member
    MemberAction.TRANSFER_MAIN_ADMIN -> R.string.members_transfer_main_admin
}

private fun MemberAction.bodyRes(): Int = when (this) {
    MemberAction.SUSPEND -> R.string.members_suspend_body
    MemberAction.REACTIVATE -> R.string.members_reactivate_body
    MemberAction.DEPART -> R.string.members_depart_body
    MemberAction.MAKE_ADMIN, MemberAction.MAKE_MEMBER -> R.string.members_role_change_body
    MemberAction.TRANSFER_MAIN_ADMIN -> R.string.members_transfer_body
}
