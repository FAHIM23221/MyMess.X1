package com.smarttransparentmess.erp.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.operations.OperationsCalculator
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.domain.operations.formatMealUnits
import com.smarttransparentmess.erp.presentation.OperationsUiState
import com.smarttransparentmess.erp.presentation.OperationsViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.LoadingContent
import com.smarttransparentmess.erp.ui.messageRes
import com.smarttransparentmess.erp.ui.theme.Spacing

private enum class OperationsTab(val titleRes: Int) {
    MEALS(R.string.operations_meals),
    RICE(R.string.operations_rice),
    EXPENSES(R.string.operations_expenses),
    CASH_BOOK(R.string.operations_cash_book),
}

@Composable
fun OperationsScreen(
    state: OperationsUiState,
    viewModel: OperationsViewModel,
    canManage: Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snapshot = state.snapshot

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.XLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.operations_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.operations_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = viewModel::refresh,
                enabled = !state.isLoading && !state.isSubmitting,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
            }
        }

        OperationsFeedback(state, viewModel::clearFeedback)
        if (state.isLoading && snapshot != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when {
            snapshot == null && state.isLoading -> LoadingContent(
                message = stringResource(R.string.operations_loading),
                modifier = Modifier.fillMaxWidth(),
            )
            snapshot == null -> InfoCard(
                title = stringResource(R.string.operations_title),
                body = stringResource(R.string.operations_no_data),
            )
            else -> {
                OperationsSummaryCard(snapshot)
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    OperationsTab.entries.forEachIndexed { index, tab ->
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
                when (OperationsTab.entries[selectedTab]) {
                    OperationsTab.MEALS -> OperationsMealSection(snapshot, state, viewModel, canManage)
                    OperationsTab.RICE -> OperationsRiceSection(snapshot, state, viewModel, canManage)
                    OperationsTab.EXPENSES -> OperationsExpenseSection(snapshot, state, viewModel, canManage)
                    OperationsTab.CASH_BOOK -> OperationsCashBookSection(snapshot)
                }
            }
        }
    }
}

@Composable
private fun OperationsSummaryCard(snapshot: OperationsSnapshot) {
    val summary = remember(snapshot) { OperationsCalculator.summarize(snapshot) }
    FormCard(title = stringResource(R.string.operations_summary)) {
        KeyValueRow(
            stringResource(R.string.operations_active_meals),
            formatMealUnits(summary.activeMealUnits),
        )
        KeyValueRow(
            stringResource(R.string.operations_rice_stock),
            stringResource(R.string.rice_grams_value, summary.riceStockGrams),
        )
        KeyValueRow(
            stringResource(R.string.operations_active_expenses),
            stringResource(R.string.bdt_amount, Money.format(summary.activeExpensesMinor)),
        )
        KeyValueRow(
            stringResource(R.string.operations_cash_balance),
            stringResource(R.string.bdt_amount, Money.format(summary.cashBalanceMinor)),
        )
    }
}

@Composable
private fun OperationsFeedback(state: OperationsUiState, onClear: () -> Unit) {
    val errorText = when {
        state.validationError != null -> stringResource(state.validationError.messageRes())
        state.operationFailed -> stringResource(R.string.operations_operation_error)
        state.loadFailed -> stringResource(R.string.operations_load_error)
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
