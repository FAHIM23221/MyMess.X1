package com.smarttransparentmess.erp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.data.operations.OperationsRepository
import com.smarttransparentmess.erp.domain.operations.ExpenseCategory
import com.smarttransparentmess.erp.domain.operations.MealSlot
import com.smarttransparentmess.erp.domain.operations.OperationsSnapshot
import com.smarttransparentmess.erp.domain.operations.RiceMovementKind
import com.smarttransparentmess.erp.domain.validation.OperationsValidation
import com.smarttransparentmess.erp.domain.validation.OperationsValidationError
import com.smarttransparentmess.erp.domain.validation.OperationsValidationException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OperationsNotice {
    MEAL_RECORDED,
    MEAL_REVERSED,
    RICE_RECORDED,
    EXPENSE_RECORDED,
    EXPENSE_REVERSED,
}

data class OperationsUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val snapshot: OperationsSnapshot? = null,
    val loadFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val validationError: OperationsValidationError? = null,
    val notice: OperationsNotice? = null,
)

class OperationsViewModel(
    private val repository: OperationsRepository?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperationsUiState())
    val uiState: StateFlow<OperationsUiState> = _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var activeCanManage = false
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private val requestIds = mutableMapOf<String, String>()

    fun loadForUser(userId: String, canManage: Boolean, force: Boolean = false) {
        val unchanged = activeUserId == userId && activeCanManage == canManage
        if (!force && unchanged && (_uiState.value.isLoading || _uiState.value.snapshot != null)) return
        val activeRepository = repository ?: return
        val retained = _uiState.value.snapshot.takeIf { activeUserId == userId }
        activeUserId = userId
        activeCanManage = canManage
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
        val userId = activeUserId ?: return
        loadForUser(userId, activeCanManage, force = true)
    }

    fun recordMeal(
        memberId: String,
        mealDate: String,
        slot: MealSlot,
        quantityText: String,
        note: String,
    ) {
        val entry = validate {
            val selectedMember = OperationsValidation.requireSelection(memberId)
            val date = OperationsValidation.pastOrToday(mealDate, dhakaToday())
            val units = OperationsValidation.mealQuantityUnits(quantityText)
            val cleanNote = OperationsValidation.optionalMealNote(note)
            MealInput(selectedMember, date.toString(), slot, units, cleanNote)
        } ?: return
        submit(entry.toString(), OperationsNotice.MEAL_RECORDED) { requestId ->
            requireRepository().recordMeal(
                memberId = entry.memberId,
                mealDate = entry.date,
                slot = entry.slot,
                quantityUnits = entry.units,
                note = entry.note,
                clientRequestId = requestId,
            )
        }
    }

    fun reverseMeal(entryId: String, reason: String) {
        if (entryId.isBlank()) return
        val cleanReason = validate { OperationsValidation.reversalReason(reason) } ?: return
        submit(null, OperationsNotice.MEAL_REVERSED) {
            requireRepository().reverseMeal(entryId, cleanReason)
        }
    }

    fun recordRice(kind: RiceMovementKind, quantityText: String, note: String) {
        val entry = validate {
            RiceInput(
                kind = kind,
                grams = OperationsValidation.riceQuantityGrams(quantityText),
                note = OperationsValidation.requiredNote(note),
            )
        } ?: return
        submit(entry.toString(), OperationsNotice.RICE_RECORDED) { requestId ->
            requireRepository().recordRice(entry.kind, entry.grams, entry.note, requestId)
        }
    }

    fun recordExpense(
        category: ExpenseCategory,
        amountText: String,
        spentOn: String,
        description: String,
        reference: String,
    ) {
        val entry = validate {
            ExpenseInput(
                category = category,
                amountMinor = OperationsValidation.expenseAmountMinor(amountText),
                spentOn = OperationsValidation.pastOrToday(spentOn, dhakaToday()).toString(),
                description = OperationsValidation.expenseDescription(description),
                reference = OperationsValidation.transactionReference(reference),
            )
        } ?: return
        submit(entry.toString(), OperationsNotice.EXPENSE_RECORDED) { requestId ->
            requireRepository().recordExpense(
                category = entry.category,
                amountMinor = entry.amountMinor,
                spentOn = entry.spentOn,
                description = entry.description,
                reference = entry.reference,
                clientRequestId = requestId,
            )
        }
    }

    fun reverseExpense(expenseId: String, reason: String) {
        if (expenseId.isBlank()) return
        val cleanReason = validate { OperationsValidation.reversalReason(reason) } ?: return
        submit(null, OperationsNotice.EXPENSE_REVERSED) {
            requireRepository().reverseExpense(expenseId, cleanReason)
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
        activeCanManage = false
        requestIds.clear()
        _uiState.value = OperationsUiState()
    }

    private fun dhakaToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Dhaka"))

    private fun <T> validate(block: () -> T): T? = try {
        block()
    } catch (error: OperationsValidationException) {
        _uiState.value = _uiState.value.copy(
            validationError = error.error,
            operationFailed = false,
            notice = null,
        )
        null
    }

    private fun submit(
        requestKey: String?,
        successNotice: OperationsNotice,
        action: suspend (String) -> Unit,
    ) {
        if (_uiState.value.isSubmitting || !activeCanManage) return
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

    private fun requireRepository(): OperationsRepository =
        repository ?: error("Supabase is not configured")

    private data class MealInput(
        val memberId: String,
        val date: String,
        val slot: MealSlot,
        val units: Int,
        val note: String?,
    )

    private data class RiceInput(
        val kind: RiceMovementKind,
        val grams: Int,
        val note: String,
    )

    private data class ExpenseInput(
        val category: ExpenseCategory,
        val amountMinor: Long,
        val spentOn: String,
        val description: String,
        val reference: String?,
    )

    class Factory(
        private val repository: OperationsRepository?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OperationsViewModel::class.java))
            return OperationsViewModel(repository) as T
        }
    }
}
