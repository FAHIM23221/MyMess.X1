package com.smarttransparentmess.erp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.data.erp.ExtendedErpRepository
import com.smarttransparentmess.erp.domain.erp.ErpAction
import com.smarttransparentmess.erp.domain.erp.ExtendedErpSnapshot
import com.smarttransparentmess.erp.domain.erp.ExtendedErpValidation
import com.smarttransparentmess.erp.domain.erp.ExtendedErpValidationError
import com.smarttransparentmess.erp.domain.erp.ExtendedErpValidationException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExtendedErpNotice {
    OPERATION_SUCCEEDED,
    MEMBER_NOTIFICATION_SAVED,
}

data class ExtendedErpUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val snapshot: ExtendedErpSnapshot? = null,
    val loadFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val validationError: ExtendedErpValidationError? = null,
    val notice: ExtendedErpNotice? = null,
)

class ExtendedErpViewModel(
    private val repository: ExtendedErpRepository?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExtendedErpUiState())
    val uiState: StateFlow<ExtendedErpUiState> = _uiState.asStateFlow()

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
                    _uiState.value = _uiState.value.copy(isLoading = false, snapshot = snapshot)
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

    fun refresh() = activeUserId?.let { loadForUser(it, force = true) }

    fun execute(action: ErpAction, input: Map<String, String>) {
        val fields = try {
            ExtendedErpValidation.validate(action, input)
        } catch (error: ExtendedErpValidationException) {
            _uiState.value = _uiState.value.copy(
                validationError = error.error,
                operationFailed = false,
                notice = null,
            )
            return
        }
        if (_uiState.value.isSubmitting) return
        val userId = activeUserId ?: return
        val requestKey = action.wireValue + fields.toSortedMap().toString()
        val requestId = requestIds.getOrPut(requestKey) { UUID.randomUUID().toString() }
        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                operationFailed = false,
                validationError = null,
                notice = null,
            )
            try {
                requireRepository().execute(action, fields, requestId)
                val refreshed = try {
                    requireRepository().loadSnapshot()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (activeUserId == userId) {
                    requestIds.remove(requestKey)
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        snapshot = refreshed ?: _uiState.value.snapshot,
                        loadFailed = refreshed == null,
                        notice = if (action == ErpAction.SEND_MEMBER_NOTIFICATION) {
                            ExtendedErpNotice.MEMBER_NOTIFICATION_SAVED
                        } else {
                            ExtendedErpNotice.OPERATION_SUCCEEDED
                        },
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
        _uiState.value = ExtendedErpUiState()
    }

    private fun requireRepository(): ExtendedErpRepository =
        repository ?: error("Supabase is not configured")

    class Factory(private val repository: ExtendedErpRepository?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ExtendedErpViewModel::class.java))
            return ExtendedErpViewModel(repository) as T
        }
    }
}
