package com.smarttransparentmess.erp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.data.members.MemberManagementRepository
import com.smarttransparentmess.erp.domain.members.MemberManagementSnapshot
import com.smarttransparentmess.erp.domain.members.OnboardingStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.validation.MemberManagementValidation
import com.smarttransparentmess.erp.domain.validation.MemberManagementValidationError
import com.smarttransparentmess.erp.domain.validation.MemberManagementValidationException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MemberManagementNotice {
    MEMBER_ADDED,
    INVITATION_SENT,
    DELIVERY_FAILED,
    DELIVERY_UNCONFIRMED,
    REQUEST_REJECTED,
    INVITATION_CANCELLED,
    PROFILE_UPDATED,
    LIFECYCLE_UPDATED,
    ROLE_UPDATED,
    MAIN_ADMIN_TRANSFERRED,
}

data class MemberManagementUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val snapshot: MemberManagementSnapshot? = null,
    val loadFailed: Boolean = false,
    val operationFailed: Boolean = false,
    val validationError: MemberManagementValidationError? = null,
    val notice: MemberManagementNotice? = null,
    val deliveryDependency: String? = null,
)

class MemberManagementViewModel(
    private val repository: MemberManagementRepository?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemberManagementUiState())
    val uiState: StateFlow<MemberManagementUiState> = _uiState.asStateFlow()

    private var activeUserId: String? = null
    private var activeCanManage = false
    private var loadJob: Job? = null
    private var operationJob: Job? = null
    private val requestIds = mutableMapOf<String, String>()

    fun loadForUser(userId: String, canManage: Boolean, force: Boolean = false) {
        if (!canManage) {
            clear()
            return
        }
        val unchanged = activeUserId == userId && activeCanManage
        if (!force && unchanged && (_uiState.value.isLoading || _uiState.value.snapshot != null)) return
        val activeRepository = repository ?: return
        val retained = _uiState.value.snapshot.takeIf { activeUserId == userId }
        activeUserId = userId
        activeCanManage = true
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

    fun refresh() {
        val userId = activeUserId ?: return
        loadForUser(userId, activeCanManage, force = true)
    }

    fun addMember(
        fullName: String,
        email: String,
        phoneNumber: String,
        memberId: String,
        joinDate: String,
        intendedRole: AppRole,
    ) {
        val entry = validate {
            MemberManagementValidation.newMember(
                fullName, email, phoneNumber, memberId, joinDate, intendedRole,
            )
        } ?: return
        if (_uiState.value.isSubmitting || !activeCanManage) return
        val userId = activeUserId ?: return
        val requestKey = entry.toString()
        val requestId = requestIds.getOrPut(requestKey) { UUID.randomUUID().toString() }
        operationJob = viewModelScope.launch {
            beginSubmit()
            try {
                val result = requireRepository().requestOnboarding(entry, requestId)
                val refreshed = refreshAfterMutation()
                if (activeUserId == userId) {
                    if (result.status in setOf(OnboardingStatus.ACTIVE, OnboardingStatus.INVITATION_SENT)) {
                        requestIds.remove(requestKey)
                    }
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        snapshot = refreshed ?: _uiState.value.snapshot,
                        loadFailed = refreshed == null,
                        operationFailed = result.status == OnboardingStatus.REQUEST_REJECTED,
                        notice = when (result.status) {
                            OnboardingStatus.ACTIVE -> MemberManagementNotice.MEMBER_ADDED
                            OnboardingStatus.INVITATION_SENT -> MemberManagementNotice.INVITATION_SENT
                            OnboardingStatus.DELIVERY_FAILED -> MemberManagementNotice.DELIVERY_FAILED
                            OnboardingStatus.DELIVERY_UNCONFIRMED -> MemberManagementNotice.DELIVERY_UNCONFIRMED
                            OnboardingStatus.REQUEST_REJECTED -> MemberManagementNotice.REQUEST_REJECTED
                        },
                        deliveryDependency = result.dependency,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        operationFailed = true,
                        notice = MemberManagementNotice.DELIVERY_UNCONFIRMED,
                        deliveryDependency = "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
                    )
                }
            }
        }
    }

    fun resendInvitation(invitationId: String) {
        if (invitationId.isBlank() || _uiState.value.isSubmitting || !activeCanManage) return
        val userId = activeUserId ?: return
        operationJob = viewModelScope.launch {
            beginSubmit()
            try {
                val result = requireRepository().resendInvitation(invitationId)
                val refreshed = refreshAfterMutation()
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        snapshot = refreshed ?: _uiState.value.snapshot,
                        loadFailed = refreshed == null,
                        operationFailed = result.status == OnboardingStatus.REQUEST_REJECTED,
                        notice = when (result.status) {
                            OnboardingStatus.ACTIVE -> MemberManagementNotice.MEMBER_ADDED
                            OnboardingStatus.INVITATION_SENT -> MemberManagementNotice.INVITATION_SENT
                            OnboardingStatus.DELIVERY_FAILED -> MemberManagementNotice.DELIVERY_FAILED
                            OnboardingStatus.DELIVERY_UNCONFIRMED -> MemberManagementNotice.DELIVERY_UNCONFIRMED
                            OnboardingStatus.REQUEST_REJECTED -> MemberManagementNotice.REQUEST_REJECTED
                        },
                        deliveryDependency = result.dependency,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeUserId == userId) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        operationFailed = true,
                        notice = MemberManagementNotice.DELIVERY_UNCONFIRMED,
                        deliveryDependency = "SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP",
                    )
                }
            }
        }
    }

    fun cancelInvitation(invitationId: String) {
        if (invitationId.isBlank()) return
        mutate(MemberManagementNotice.INVITATION_CANCELLED) {
            requireRepository().cancelInvitation(invitationId)
        }
    }

    fun updateMember(
        targetUserId: String,
        fullName: String,
        phoneNumber: String,
        memberId: String,
        joinDate: String,
    ) {
        val entry = validate {
            MemberManagementValidation.profileEdit(fullName, phoneNumber, memberId, joinDate)
        } ?: return
        mutate(MemberManagementNotice.PROFILE_UPDATED) {
            requireRepository().updateMember(targetUserId, entry)
        }
    }

    fun setLifecycle(targetUserId: String, status: String, reason: String) {
        val cleanReason = validate { MemberManagementValidation.reason(reason) } ?: return
        if (status !in setOf("ACTIVE", "PENDING", "SUSPENDED", "DEPARTED")) return
        mutate(MemberManagementNotice.LIFECYCLE_UPDATED) {
            requireRepository().setLifecycle(targetUserId, status, cleanReason)
        }
    }

    fun setRole(targetUserId: String, role: AppRole, reason: String) {
        val cleanReason = validate { MemberManagementValidation.reason(reason, minimumLength = 8) } ?: return
        mutate(MemberManagementNotice.ROLE_UPDATED) {
            requireRepository().setRole(targetUserId, role, cleanReason)
        }
    }

    fun transferMainAdmin(targetUserId: String, reason: String) {
        val cleanReason = validate { MemberManagementValidation.reason(reason, minimumLength = 8) } ?: return
        mutate(MemberManagementNotice.MAIN_ADMIN_TRANSFERRED) {
            requireRepository().transferMainAdmin(targetUserId, cleanReason)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            operationFailed = false,
            validationError = null,
            notice = null,
            deliveryDependency = null,
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
        _uiState.value = MemberManagementUiState()
    }

    private fun beginSubmit() {
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            operationFailed = false,
            validationError = null,
            notice = null,
            deliveryDependency = null,
        )
    }

    private fun mutate(successNotice: MemberManagementNotice, action: suspend () -> Unit) {
        if (_uiState.value.isSubmitting || !activeCanManage) return
        val userId = activeUserId ?: return
        operationJob = viewModelScope.launch {
            beginSubmit()
            try {
                action()
                val refreshed = refreshAfterMutation()
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

    private suspend fun refreshAfterMutation(): MemberManagementSnapshot? = try {
        requireRepository().loadSnapshot()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun <T> validate(block: () -> T): T? = try {
        block()
    } catch (error: MemberManagementValidationException) {
        _uiState.value = _uiState.value.copy(
            validationError = error.error,
            operationFailed = false,
            notice = null,
        )
        null
    }

    private fun requireRepository(): MemberManagementRepository =
        repository ?: error("Supabase is not configured")

    class Factory(
        private val repository: MemberManagementRepository?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MemberManagementViewModel::class.java))
            return MemberManagementViewModel(repository) as T
        }
    }
}
