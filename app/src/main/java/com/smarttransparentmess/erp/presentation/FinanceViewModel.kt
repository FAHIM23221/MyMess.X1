package com.smarttransparentmess.erp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.data.finance.FinanceRepository
import com.smarttransparentmess.erp.domain.finance.MemberFinance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FinanceUiState(
    val isLoading: Boolean = false,
    val data: MemberFinance? = null,
    val hasError: Boolean = false,
    val hasLoaded: Boolean = false,
)

class FinanceViewModel(
    private val repository: FinanceRepository?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    private var loadedUserId: String? = null
    private var loadJob: Job? = null

    fun loadForUser(userId: String, force: Boolean = false) {
        if (!force && loadedUserId == userId && (_uiState.value.hasLoaded || _uiState.value.isLoading)) return
        val activeRepository = repository ?: return

        val retainedData = _uiState.value.data.takeIf { loadedUserId == userId }
        loadedUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Never show one account's ledger while another account is being loaded.
            _uiState.value = FinanceUiState(isLoading = true, data = retainedData)
            try {
                val data = activeRepository.loadMyFinance()
                if (loadedUserId == userId) {
                    _uiState.value = FinanceUiState(data = data, hasLoaded = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (loadedUserId == userId) {
                    _uiState.value = FinanceUiState(data = _uiState.value.data, hasError = true, hasLoaded = true)
                }
            }
        }
    }

    fun refresh() {
        loadedUserId?.let { loadForUser(it, force = true) }
    }

    fun clear() {
        loadJob?.cancel()
        loadJob = null
        loadedUserId = null
        _uiState.value = FinanceUiState()
    }

    class Factory(
        private val repository: FinanceRepository?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
            return FinanceViewModel(repository) as T
        }
    }
}
