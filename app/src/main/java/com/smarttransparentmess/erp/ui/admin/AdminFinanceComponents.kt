package com.smarttransparentmess.erp.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.BillingPeriod
import com.smarttransparentmess.erp.domain.finance.FinanceMember
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.messageRes

@Composable
fun AdminFinanceFeedback(
    state: AdminFinanceUiState,
    onClear: () -> Unit,
) {
    val errorText = when {
        state.validationError != null -> stringResource(state.validationError.messageRes())
        state.operationFailed -> stringResource(R.string.admin_finance_operation_error)
        state.loadFailed -> stringResource(R.string.admin_finance_load_error)
        else -> null
    }
    if (errorText != null) {
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(errorText, color = MaterialTheme.colorScheme.error)
        }
    }
    state.notice?.let { notice ->
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(notice.messageRes()), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MemberPeriodPicker(
    snapshot: AdminFinanceSnapshot,
    memberId: String,
    onMemberSelected: (String) -> Unit,
    periodId: String,
    onPeriodSelected: (String) -> Unit,
    enabled: Boolean,
) {
    val members = snapshot.members.filter { it.accountStatus == AccountStatus.ACTIVE }
    val periods = snapshot.periods.filter { it.status == com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus.OPEN }
    SelectionField(
        label = stringResource(R.string.select_member),
        options = members,
        selected = members.firstOrNull { it.userId == memberId },
        display = ::memberDisplay,
        onSelected = { onMemberSelected(it.userId) },
        enabled = enabled,
        emptyText = stringResource(R.string.no_active_members),
    )
    SelectionField(
        label = stringResource(R.string.select_period),
        options = periods,
        selected = periods.firstOrNull { it.id == periodId },
        display = ::periodDisplay,
        onSelected = { onPeriodSelected(it.id) },
        enabled = enabled,
        emptyText = stringResource(R.string.no_open_periods),
    )
}

@Composable
fun memberDisplay(member: FinanceMember): String =
    member.memberId?.let { "${member.fullName} · $it" } ?: member.fullName

@Composable
fun periodDisplay(period: BillingPeriod): String =
    "${period.label} · ${period.startsOn}—${period.endsOn}"
