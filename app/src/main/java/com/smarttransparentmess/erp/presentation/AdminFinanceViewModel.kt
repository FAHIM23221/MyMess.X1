package com.smarttransparentmess.erp.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.core.receipt.ReceiptDocument
import com.smarttransparentmess.erp.data.finance.AdminFinanceRepository
import com.smarttransparentmess.erp.domain.finance.AdminFinanceSnapshot
import com.smarttransparentmess.erp.domain.finance.AdminPaymentReceipt
import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.domain.validation.FinanceEntryValidator
import com.smarttransparentmess.erp.domain.validation.FinanceValidationError
import com.smarttransparentmess.erp.domain.validation.FinanceValidationException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdminFinanceNotice {
    PERIOD_CREATED,
    PERIOD_CLOSED,
    CHARGE_CREATED,
    ADJUSTMENT_CREATED,
    PAYMENT_RECORDED,
    PAYMENT_REVERSED,
    CARRY_FORWARD_COMPLETED,
}

data class AdminFinanceUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val snapshot: AdminFinanceSnapshot? = null,
    val loadFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val validationError: FinanceValidationError? = null,
    val notice: AdminFinanceNotice? = null,
)

class AdminFinanceViewModel(
    private val repository: AdminFinanceRepository?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminFinanceUiState())
    val uiState: StateFlow<AdminFinanceUiState> = _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private val requestIds = mutableMapOf<String, String>()

    fun loadForUser(userId: String, force: Boolean = false) {
        if (!force && activeUserId == userId && (_uiState.value.isLoading || _uiState.value.snapshot != null)) return
        val activeRepository = repository ?: return
        val retained = _uiState.value.snapshot.takeIf { activeUserId == userId }
        activeUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                snapshot = retained,
                loadFailed = false,
                operationFailed = false,
                validationError = null,
            )
            try {
                val snapshot = activeRepository.loadSnapshot()
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        snapshot = snapshot,
                        loadFailed = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true)
                }
            }
        }
    }

    fun refresh() {
        activeUserId?.let { loadForUser(it, force = true) }
    }

    fun createPeriod(label: String, startsOn: String, endsOn: String) {
        val entry = validate { FinanceEntryValidator.period(label, startsOn, endsOn) } ?: return
        submit(null, AdminFinanceNotice.PERIOD_CREATED) {
            requireRepository().createBillingPeriod(entry)
        }
    }

    fun closePeriod(periodId: String) {
        if (periodId.isBlank()) return
        submit(null, AdminFinanceNotice.PERIOD_CLOSED) {
            requireRepository().closeBillingPeriod(periodId)
        }
    }

    fun createCharge(
        memberId: String,
        periodId: String,
        category: ChargeCategory,
        amountText: String,
        description: String,
        dueDate: String,
    ) {
        val entry = validate {
            FinanceEntryValidator.charge(memberId, periodId, category, amountText, description, dueDate)
        } ?: return
        submit(entry.toString(), AdminFinanceNotice.CHARGE_CREATED) { requestId ->
            requireRepository().createCharge(entry, requestId)
        }
    }

    fun addAdjustment(
        memberId: String,
        periodId: String,
        kind: AdjustmentKind,
        amountText: String,
        reason: String,
    ) {
        val entry = validate {
            FinanceEntryValidator.adjustment(memberId, periodId, kind, amountText, reason)
        } ?: return
        submit(entry.toString(), AdminFinanceNotice.ADJUSTMENT_CREATED) { requestId ->
            requireRepository().addAdjustment(entry, requestId)
        }
    }

    fun recordPayment(
        memberId: String,
        periodId: String,
        amountText: String,
        method: PaymentMethod,
        paymentDate: String,
        note: String,
        externalReference: String,
    ) {
        val entry = validate {
            FinanceEntryValidator.payment(
                memberId,
                periodId,
                amountText,
                method,
                paymentDate,
                note,
                externalReference,
            )
        } ?: return
        submit(entry.toString(), AdminFinanceNotice.PAYMENT_RECORDED) { requestId ->
            requireRepository().recordPayment(entry, requestId)
        }
    }

    fun reversePayment(paymentId: String, reason: String) {
        val cleanReason = validate { FinanceEntryValidator.reversalReason(reason) } ?: return
        submit(null, AdminFinanceNotice.PAYMENT_REVERSED) {
            requireRepository().reversePayment(paymentId, cleanReason)
        }
    }

    fun carryForward(memberId: String, sourcePeriodId: String, targetPeriodId: String) {
        if (memberId.isBlank() || sourcePeriodId.isBlank() || targetPeriodId.isBlank()) {
            setValidationError(FinanceValidationError.REQUIRED_SELECTION)
            return
        }
        if (sourcePeriodId == targetPeriodId) {
            setValidationError(FinanceValidationError.DATE_RANGE)
            return
        }
        submit(null, AdminFinanceNotice.CARRY_FORWARD_COMPLETED) {
            requireRepository().carryForward(memberId, sourcePeriodId, targetPeriodId)
        }
    }

    fun exportReceipt(context: Context, record: AdminPaymentReceipt, destination: Uri) {
        runReceiptOperation {
            withContext(Dispatchers.IO) { ReceiptDocument.write(context, record, destination) }
        }
    }

    fun shareReceipt(context: Context, record: AdminPaymentReceipt) {
        runReceiptOperation {
            val file = withContext(Dispatchers.IO) { ReceiptDocument.createShareFile(context, record) }
            ReceiptDocument.share(context, record, file)
        }
    }

    private fun runReceiptOperation(action: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                operationFailed = false,
                validationError = null,
                notice = null,
            )
            try {
                action()
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, operationFailed = true)
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            operationFailed = false,
            validationError = null,
            notice = null,
        )
    }

    fun clear() {
        loadJob?.cancel()
        operationJob?.cancel()
        loadJob = null
        operationJob = null
        activeUserId = null
        requestIds.clear()
        _uiState.value = AdminFinanceUiState()
    }

    private fun <T> validate(block: () -> T): T? = try {
        block()
    } catch (error: FinanceValidationException) {
        setValidationError(error.error)
        null
    }

    private fun setValidationError(error: FinanceValidationError) {
        _uiState.value = _uiState.value.copy(
            validationError = error,
            operationFailed = false,
            notice = null,
        )
    }

    private fun submit(
        requestKey: String?,
        successNotice: AdminFinanceNotice,
        action: suspend (String) -> Unit,
    ) {
        if (_uiState.value.isSubmitting) return
        val userId = activeUserId ?: return
        val requestId = requestKey?.let { key ->
            requestIds.getOrPut(key) { UUID.randomUUID().toString() }
        }.orEmpty()

        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                operationFailed = false,
                validationError = null,
                notice = null,
            )
            try {
                action(requestId)
                requestKey?.let(requestIds::remove)
                val refreshed = try {
                    requireRepository().loadSnapshot()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        snapshot = refreshed ?: _uiState.value.snapshot,
                        loadFailed = refreshed == null,
                        notice = successNotice,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(isSubmitting = false, operationFailed = true)
                }
            }
        }
    }

    private fun requireRepository(): AdminFinanceRepository =
        repository ?: error("Supabase is not configured")

    class Factory(
        private val repository: AdminFinanceRepository?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AdminFinanceViewModel::class.java))
            return AdminFinanceViewModel(repository) as T
        }
    }
}
