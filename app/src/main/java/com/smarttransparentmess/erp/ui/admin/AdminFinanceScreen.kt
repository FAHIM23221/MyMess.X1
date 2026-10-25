package com.smarttransparentmess.erp.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdminFinanceCalculator
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.BillingPeriod
import com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.presentation.AdminFinanceNotice
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.presentation.AdminFinanceViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.LoadingContent
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

private enum class AdminFinanceTab(val titleRes: Int) {
    OVERVIEW(R.string.admin_finance_overview),
    PAYMENTS(R.string.admin_finance_payments),
    CHARGES(R.string.admin_finance_charges),
    PERIODS(R.string.admin_finance_periods),
}

@Composable
fun AdminFinanceScreen(
    state: AdminFinanceUiState,
    viewModel: AdminFinanceViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMemberId by rememberSaveable { mutableStateOf("") }
    var selectedPeriodId by rememberSaveable { mutableStateOf("") }
    var overviewPeriodId by rememberSaveable { mutableStateOf("") }
    val snapshot = state.snapshot

    LaunchedEffect(snapshot) {
        if (snapshot != null) {
            val activeIds = snapshot.members.filter { it.accountStatus == AccountStatus.ACTIVE }.map { it.userId }
            if (selectedMemberId !in activeIds) selectedMemberId = activeIds.firstOrNull().orEmpty()
            val openIds = snapshot.periods.filter { it.status == BillingPeriodStatus.OPEN }.map { it.id }
            if (selectedPeriodId !in openIds) selectedPeriodId = openIds.firstOrNull().orEmpty()
            if (overviewPeriodId !in snapshot.periods.map { it.id }) {
                overviewPeriodId = snapshot.periods.firstOrNull()?.id.orEmpty()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.admin_finance_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.isLoading && !state.isSubmitting,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                }
            }
            Text(
                text = stringResource(R.string.admin_finance_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AdminFinanceFeedback(state, viewModel::clearFeedback)
        if (state.isLoading && snapshot != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            snapshot == null && state.isLoading -> LoadingContent(
                message = stringResource(R.string.finance_loading),
                modifier = Modifier.fillMaxWidth(),
            )
            snapshot == null -> InfoCard(
                title = stringResource(R.string.admin_finance_title),
                body = stringResource(R.string.admin_finance_no_data),
            )
            else -> {
                Text(
                    text = stringResource(
                        R.string.admin_finance_last_updated,
                        snapshot.loadedAt.substringBefore('.'),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TabRow(selectedTabIndex = selectedTab) {
                    AdminFinanceTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                viewModel.clearFeedback()
                            },
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
                when (AdminFinanceTab.entries[selectedTab]) {
                    AdminFinanceTab.OVERVIEW -> AdminFinanceOverview(
                        snapshot = snapshot,
                        periodId = overviewPeriodId,
                        onPeriodSelected = { overviewPeriodId = it },
                        onTabSelected = { selectedTab = it },
                    )
                    AdminFinanceTab.PAYMENTS -> AdminPaymentSection(
                        snapshot = snapshot,
                        state = state,
                        viewModel = viewModel,
                        memberId = selectedMemberId,
                        onMemberSelected = { selectedMemberId = it },
                        periodId = selectedPeriodId,
                        onPeriodSelected = { selectedPeriodId = it },
                    )
                    AdminFinanceTab.CHARGES -> AdminChargeSection(
                        snapshot = snapshot,
                        state = state,
                        viewModel = viewModel,
                        memberId = selectedMemberId,
                        onMemberSelected = { selectedMemberId = it },
                        periodId = selectedPeriodId,
                        onPeriodSelected = { selectedPeriodId = it },
                    )
                    AdminFinanceTab.PERIODS -> AdminPeriodSection(
                        snapshot = snapshot,
                        state = state,
                        viewModel = viewModel,
                        memberId = selectedMemberId,
                        onMemberSelected = { selectedMemberId = it },
                        periodId = selectedPeriodId,
                        onPeriodSelected = { selectedPeriodId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminFinanceOverview(
    snapshot: AdminFinanceSnapshot,
    periodId: String,
    onPeriodSelected: (String) -> Unit,
    onTabSelected: (Int) -> Unit,
) {
    val period = snapshot.periods.firstOrNull { it.id == periodId }
    val overview = remember(snapshot, periodId) {
        AdminFinanceCalculator.calculate(snapshot, periodId)
    }
    SelectionField(
        label = stringResource(R.string.select_period),
        options = snapshot.periods,
        selected = period,
        display = ::periodDisplay,
        onSelected = { onPeriodSelected(it.id) },
        emptyText = stringResource(R.string.no_periods),
    )
    FormCard(title = period?.label ?: stringResource(R.string.no_periods)) {
        KeyValueRow(stringResource(R.string.admin_finance_active_members), overview.activeMembers.toString())
        KeyValueRow(
            stringResource(R.string.admin_finance_total_charges),
            stringResource(R.string.bdt_amount, Money.format(overview.chargesMinor)),
        )
        KeyValueRow(
            stringResource(R.string.admin_finance_valid_payments),
            stringResource(R.string.bdt_amount, Money.format(overview.validPaymentsMinor)),
        )
        KeyValueRow(
            stringResource(R.string.finance_opening_due),
            stringResource(R.string.bdt_amount, Money.format(overview.openingDueMinor)),
        )
        KeyValueRow(
            stringResource(R.string.finance_debit_adjustments),
            stringResource(R.string.bdt_amount, Money.format(overview.debitAdjustmentsMinor)),
        )
        KeyValueRow(
            stringResource(R.string.finance_credit_adjustments),
            stringResource(R.string.bdt_amount, Money.format(overview.creditAdjustmentsMinor)),
        )
        KeyValueRow(
            stringResource(R.string.admin_finance_outstanding),
            stringResource(R.string.bdt_amount, Money.format(overview.outstandingDueMinor)),
        )
        KeyValueRow(
            stringResource(R.string.admin_finance_advance),
            stringResource(R.string.bdt_amount, Money.format(overview.advanceCreditMinor)),
        )
    }
    OutlinedButton(onClick = { onTabSelected(1) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.admin_finance_quick_payment))
    }
    OutlinedButton(onClick = { onTabSelected(2) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.admin_finance_quick_charge))
    }
    OutlinedButton(onClick = { onTabSelected(3) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.admin_finance_manage_periods))
    }
    if (snapshot.payments.isNotEmpty()) {
        FormCard(title = stringResource(R.string.payment_history)) {
            snapshot.payments.take(3).forEach { record ->
                Text(
                    "${record.memberName} · ${stringResource(R.string.bdt_amount, Money.format(record.payment.amountMinor))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${record.periodLabel} · ${record.payment.receiptNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdminPeriodSection(
    snapshot: AdminFinanceSnapshot,
    state: AdminFinanceUiState,
    viewModel: AdminFinanceViewModel,
    memberId: String,
    onMemberSelected: (String) -> Unit,
    periodId: String,
    onPeriodSelected: (String) -> Unit,
) {
    val zone = remember { ZoneId.of("Asia/Dhaka") }
    val now = remember { LocalDate.now(zone) }
    var label by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf(now.withDayOfMonth(1).toString()) }
    var end by rememberSaveable { mutableStateOf(now.withDayOfMonth(now.lengthOfMonth()).toString()) }
    var sourcePeriodId by rememberSaveable { mutableStateOf("") }
    var periodToClose by remember { mutableStateOf<BillingPeriod?>(null) }
    val closedPeriods = snapshot.periods.filter { it.status == BillingPeriodStatus.CLOSED }

    LaunchedEffect(closedPeriods) {
        if (sourcePeriodId !in closedPeriods.map { it.id }) {
            sourcePeriodId = closedPeriods.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(state.notice) {
        when (state.notice) {
            AdminFinanceNotice.PERIOD_CREATED -> label = ""
            AdminFinanceNotice.PERIOD_CLOSED -> periodToClose = null
            AdminFinanceNotice.CARRY_FORWARD_COMPLETED -> sourcePeriodId = ""
            else -> Unit
        }
    }

    FormCard(
        title = stringResource(R.string.create_period),
        subtitle = stringResource(R.string.carry_forward_body),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.period_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            value = start,
            onValueChange = { start = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.period_start)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.period_end)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isSubmitting,
        )
        PrimaryActionButton(
            text = stringResource(R.string.create_period),
            onClick = { viewModel.createPeriod(label, start, end) },
            enabled = !state.isSubmitting,
            loading = state.isSubmitting,
        )
    }

    FormCard(title = stringResource(R.string.admin_finance_periods)) {
        if (snapshot.periods.isEmpty()) {
            Text(stringResource(R.string.no_periods))
        } else {
            snapshot.periods.forEach { period ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                    Text(periodDisplay(period), style = MaterialTheme.typography.bodyMedium)
                    StatusPill(
                        text = stringResource(
                            if (period.status == BillingPeriodStatus.OPEN) {
                                R.string.finance_period_open
                            } else {
                                R.string.finance_period_closed
                            },
                        ),
                    )
                    if (period.status == BillingPeriodStatus.OPEN) {
                        TextButton(
                            onClick = { periodToClose = period },
                            enabled = !state.isSubmitting,
                        ) { Text(stringResource(R.string.close_period)) }
                    }
                }
            }
        }
    }

    FormCard(
        title = stringResource(R.string.carry_forward),
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
            label = stringResource(R.string.source_closed_period),
            options = closedPeriods,
            selected = closedPeriods.firstOrNull { it.id == sourcePeriodId },
            display = ::periodDisplay,
            onSelected = { sourcePeriodId = it.id },
            enabled = !state.isSubmitting,
            emptyText = stringResource(R.string.no_closed_periods),
        )
        PrimaryActionButton(
            text = stringResource(R.string.carry_forward),
            onClick = { viewModel.carryForward(memberId, sourcePeriodId, periodId) },
            enabled = !state.isSubmitting && memberId.isNotBlank() && sourcePeriodId.isNotBlank() && periodId.isNotBlank(),
            loading = state.isSubmitting,
        )
    }

    periodToClose?.let { period ->
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) periodToClose = null },
            title = { Text(stringResource(R.string.close_period)) },
            text = { Text(stringResource(R.string.close_period_confirmation, period.label)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.closePeriod(period.id) },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { periodToClose = null },
                    enabled = !state.isSubmitting,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
