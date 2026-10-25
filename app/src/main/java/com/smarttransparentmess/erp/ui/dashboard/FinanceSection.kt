package com.smarttransparentmess.erp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.domain.finance.PaymentReceipt
import com.smarttransparentmess.erp.domain.finance.PeriodFinance
import com.smarttransparentmess.erp.presentation.FinanceUiState
import com.smarttransparentmess.erp.ui.InfoCard
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.labelRes

@Composable
fun FinanceSection(
    state: FinanceUiState,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.finance_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        when {
            state.data == null && state.hasError -> {
                InfoCard(
                    title = stringResource(R.string.finance_title),
                    body = stringResource(R.string.finance_error),
                )
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
            }
            state.data == null -> InfoCard(
                title = stringResource(R.string.finance_title),
                body = stringResource(R.string.finance_loading),
            )
            else -> {
                val data = requireNotNull(state.data)
                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.hasError) {
                    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.finance_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                data.currentPeriod?.let { FinanceSummaryCard(it) } ?: InfoCard(
                    title = stringResource(R.string.finance_no_period_title),
                    body = stringResource(R.string.finance_no_period_body),
                )
                RecentPayments(data.recentPayments)
            }
        }
    }
}

@Composable
private fun FinanceSummaryCard(period: PeriodFinance) {
    val balanceLabel = when {
        period.outstandingDueMinor > 0L -> stringResource(R.string.finance_due)
        period.advanceCreditMinor > 0L -> stringResource(R.string.finance_advance)
        else -> stringResource(R.string.finance_settled)
    }
    val balanceAmount = when {
        period.outstandingDueMinor > 0L -> period.outstandingDueMinor
        period.advanceCreditMinor > 0L -> period.advanceCreditMinor
        else -> 0L
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = period.periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = stringResource(
                        when (period.periodStatus) {
                            com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus.OPEN ->
                                R.string.finance_period_open
                            com.smarttransparentmess.erp.domain.finance.BillingPeriodStatus.CLOSED ->
                                R.string.finance_period_closed
                        },
                    ),
                )
            }
            Text(
                text = stringResource(R.string.finance_period_range, period.periodStart, period.periodEnd),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(balanceLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(R.string.bdt_amount, Money.format(balanceAmount)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (period.outstandingDueMinor > 0L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            LedgerLine(R.string.finance_charges, period.chargesMinor)
            LedgerLine(R.string.finance_valid_payments, period.validPaymentsMinor)
            LedgerLine(R.string.finance_opening_due, period.openingDueMinor)
            LedgerLine(R.string.finance_debit_adjustments, period.debitAdjustmentsMinor)
            LedgerLine(R.string.finance_credit_adjustments, period.creditAdjustmentsMinor)
        }
    }
}

@Composable
private fun LedgerLine(labelRes: Int, amountMinor: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.bdt_amount, Money.format(amountMinor)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecentPayments(payments: List<PaymentReceipt>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.finance_recent_payments),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (payments.isEmpty()) {
            InfoCard(
                title = stringResource(R.string.finance_no_payments_title),
                body = stringResource(R.string.finance_no_payments_body),
            )
        } else {
            payments.forEach { payment -> PaymentCard(payment) }
        }
    }
}

@Composable
private fun PaymentCard(payment: PaymentReceipt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.bdt_amount, Money.format(payment.amountMinor)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusPill(
                    text = stringResource(
                        if (payment.isValid) R.string.finance_valid else R.string.finance_reversed,
                    ),
                )
            }
            Text(
                text = "${stringResource(payment.method.labelRes())} · ${payment.paidAt.substringBefore('T')}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.finance_receipt_format, payment.receiptNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            payment.transactionReference?.let {
                Text(
                    text = stringResource(R.string.finance_transaction_ref, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            payment.note?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            payment.reversalReason?.let {
                Text(
                    text = stringResource(R.string.finance_reversal_reason, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
