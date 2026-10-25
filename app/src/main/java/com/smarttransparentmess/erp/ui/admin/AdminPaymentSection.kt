package com.smarttransparentmess.erp.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.AdminPaymentReceipt
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.presentation.AdminFinanceNotice
import com.smarttransparentmess.erp.presentation.AdminFinanceUiState
import com.smarttransparentmess.erp.presentation.AdminFinanceViewModel
import com.smarttransparentmess.erp.ui.FormCard
import com.smarttransparentmess.erp.ui.KeyValueRow
import com.smarttransparentmess.erp.ui.PrimaryActionButton
import com.smarttransparentmess.erp.ui.SelectionField
import com.smarttransparentmess.erp.ui.StatusPill
import com.smarttransparentmess.erp.ui.labelRes
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AdminPaymentSection(
    snapshot: AdminFinanceSnapshot,
    state: AdminFinanceUiState,
    viewModel: AdminFinanceViewModel,
    memberId: String,
    onMemberSelected: (String) -> Unit,
    periodId: String,
    onPeriodSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var amount by rememberSaveable { mutableStateOf("") }
    var paymentDate by rememberSaveable {
        mutableStateOf(LocalDate.now(ZoneId.of("Asia/Dhaka")).toString())
    }
    var methodName by rememberSaveable { mutableStateOf(PaymentMethod.CASH.name) }
    var reference by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var pendingReceipt by remember { mutableStateOf<AdminPaymentReceipt?>(null) }
    var reversalTarget by remember { mutableStateOf<AdminPaymentReceipt?>(null) }
    var reversalReason by rememberSaveable { mutableStateOf("") }
    val method = PaymentMethod.valueOf(methodName)
    val receiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val record = pendingReceipt
        if (uri != null && record != null) viewModel.exportReceipt(context, record, uri)
        pendingReceipt = null
    }

    LaunchedEffect(state.notice) {
        when (state.notice) {
            AdminFinanceNotice.PAYMENT_RECORDED -> {
                amount = ""
                reference = ""
                note = ""
            }
            AdminFinanceNotice.PAYMENT_REVERSED -> {
                reversalTarget = null
                reversalReason = ""
            }
            else -> Unit
        }
    }

    FormCard(title = stringResource(R.string.admin_finance_quick_payment)) {
        MemberPeriodPicker(
            snapshot = snapshot,
            memberId = memberId,
            onMemberSelected = onMemberSelected,
            periodId = periodId,
            onPeriodSelected = onPeriodSelected,
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
        SelectionField(
            label = stringResource(R.string.select_method),
            options = PaymentMethod.entries,
            selected = method,
            display = { stringResource(it.labelRes()) },
            onSelected = { methodName = it.name },
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            value = paymentDate,
            onValueChange = { paymentDate = it; viewModel.clearFeedback() },
            label = { Text(stringResource(R.string.date_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
            text = stringResource(R.string.admin_finance_quick_payment),
            onClick = {
                viewModel.recordPayment(
                    memberId = memberId,
                    periodId = periodId,
                    amountText = amount,
                    method = method,
                    paymentDate = paymentDate,
                    externalReference = reference,
                    note = note,
                )
            },
            enabled = !state.isSubmitting && memberId.isNotBlank() && periodId.isNotBlank(),
            loading = state.isSubmitting,
        )
    }

    Text(stringResource(R.string.payment_history), style = MaterialTheme.typography.titleMedium)
    if (snapshot.payments.isEmpty()) {
        Text(
            stringResource(R.string.no_payment_history),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        snapshot.payments.forEach { record ->
            FormCard(
                title = record.payment.receiptNumber,
                subtitle = "${record.memberName} · ${record.periodLabel}",
            ) {
                KeyValueRow(
                    stringResource(R.string.amount_label),
                    stringResource(R.string.bdt_amount, Money.format(record.payment.amountMinor)),
                )
                KeyValueRow(
                    stringResource(R.string.select_method),
                    stringResource(record.payment.method.labelRes()),
                )
                KeyValueRow(stringResource(R.string.date_label), record.payment.paidAt)
                KeyValueRow(
                    stringResource(R.string.reference_optional),
                    record.payment.transactionReference ?: "—",
                )
                StatusPill(
                    text = stringResource(
                        if (record.payment.isValid) {
                            R.string.payment_status_valid
                        } else {
                            R.string.payment_status_reversed
                        },
                    ),
                )
                record.payment.reversalReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        pendingReceipt = record
                        receiptLauncher.launch("${record.payment.receiptNumber}.pdf")
                    },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.download_receipt)) }
                OutlinedButton(
                    onClick = { viewModel.shareReceipt(context, record) },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.share_receipt)) }
                if (record.payment.isValid) {
                    TextButton(
                        onClick = { reversalTarget = record },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reverse_payment)) }
                }
            }
        }
    }

    reversalTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) reversalTarget = null },
            title = { Text(stringResource(R.string.reverse_payment)) },
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
                    onClick = { viewModel.reversePayment(target.payment.paymentId, reversalReason) },
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
