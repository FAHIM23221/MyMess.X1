package com.smarttransparentmess.erp.ui.operations

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.operations.CashBookDirection
import com.smarttransparentmess.erp.domain.operations.OperationsCalculator
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.KeyValueRow

@Composable
fun OperationsCashBookSection(snapshot: OperationsSnapshot) {
    val summary = remember(snapshot) { OperationsCalculator.summarize(snapshot) }
    FormCard(
        title = stringResource(R.string.operations_cash_book),
        subtitle = stringResource(R.string.cash_book_read_only),
    ) {
        KeyValueRow(
            stringResource(R.string.cash_inflow),
            stringResource(R.string.bdt_amount, Money.format(summary.cashInflowMinor)),
        )
        KeyValueRow(
            stringResource(R.string.cash_outflow),
            stringResource(R.string.bdt_amount, Money.format(summary.cashOutflowMinor)),
        )
        KeyValueRow(
            stringResource(R.string.operations_cash_balance),
            stringResource(R.string.bdt_amount, Money.format(summary.cashBalanceMinor)),
        )
    }

    Text(stringResource(R.string.cash_book_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.cashBook.isEmpty()) {
        Text(stringResource(R.string.no_cash_book_entries), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        snapshot.cashBook.forEach { entry ->
            FormCard(
                title = stringResource(
                    when (entry.sourceType) {
                        "PAYMENT" -> R.string.cash_source_payment
                        "PAYMENT_REVERSAL" -> R.string.cash_source_payment_reversal
                        "EXPENSE" -> R.string.cash_source_expense
                        "EXPENSE_REVERSAL" -> R.string.cash_source_expense_reversal
                        else -> R.string.cash_source_other
                    },
                ),
                subtitle = entry.occurredAt,
            ) {
                KeyValueRow(
                    stringResource(
                        if (entry.direction == CashBookDirection.INFLOW) {
                            R.string.cash_inflow
                        } else {
                            R.string.cash_outflow
                        },
                    ),
                    stringResource(R.string.bdt_amount, Money.format(entry.amountMinor)),
                )
                Text(entry.description, style = MaterialTheme.typography.bodySmall)
                entry.memberName?.let {
                    KeyValueRow(stringResource(R.string.select_member), it)
                }
            }
        }
    }
}
