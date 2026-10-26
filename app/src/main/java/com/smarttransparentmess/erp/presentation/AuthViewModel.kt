package com.smarttransparentmess.erp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.smarttransparentmess.erp.data.auth.AuthRepository
import com.smarttransparentmess.erp.data.auth.AuthSessionEvent
import com.smarttransparentmess.erp.data.profile.MemberProfileRepository
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.model.MemberProfile
import com.smarttransparentmess.erp.domain.validation.ProfileValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AppUiState {
    data object CheckingSession : AppUiState
    data object SetupRequired : AppUiState
    data object SignedOut : AppUiState
    data class Authorized(val profile: MemberProfile) : AppUiState
    data class ProfileUnavailable(val reason: ProfileUnavailableReason) : AppUiState
}

enum class ProfileUnavailableReason {
    INACTIVE_ACCOUNT,
    PROFILE_LOAD_FAILED,
}

enum class AuthNotice {
    SIGN_IN_FAILED,
    ADMIN_ACCESS_REQUIRED,
    ACCOUNT_NOT_ACTIVE,
    CREDENTIALS_REQUIRED,
    RECOVERY_REQUESTED,
    RECOVERY_FAILED,
    PASSWORD_MISMATCH,
    PASSWORD_TOO_SHORT,
    PASSWORD_UPDATED,
    PASSWORD_UPDATE_FAILED,
    PROFILE_SAVED,
    PROFILE_NAME_INVALID,
    PROFILE_PHONE_INVALID,
    PROFILE_SAVE_FAILED,
    SESSION_EXPIRED,
    SIGNED_OUT_LOCAL_ONLY,
}

class AuthViewModel(
    private val authRepository: AuthRepository?,
    private val profileRepository: MemberProfileRepository?,
    isConfigured: Boolean,
    passwordRecoveryPending: StateFlow<Boolean>,
    private val onPasswordRecoveryComplete: () -> Unit,
) : ViewModel() {
    private val operationMutex = Mutex()
    private var adminRoleRequiredForPendingSignIn = false

    private val _uiState = MutableStateFlow<AppUiState>(
        if (isConfigured && authRepository != null && profileRepository != null) {
            AppUiState.CheckingSession
        } else {
            AppUiState.SetupRequired
        },
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _notice = MutableStateFlow<AuthNotice?>(null)
    val notice: StateFlow<AuthNotice?> = _notice.asStateFlow()

    private val _recoveryPending = MutableStateFlow(false)
    val recoveryPending: StateFlow<Boolean> = _recoveryPending.asStateFlow()

    init {
        if (isConfigured && authRepository != null && profileRepository != null) {
            viewModelScope.launch {
                passwordRecoveryPending.collect { _recoveryPending.value = it }
            }
            viewModelScope.launch {
                try {
                    authRepository.awaitReady()
                    authRepository.sessionEvents.collect { event -> handleSessionEvent(event) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
                }
            }
        }
    }

    fun signIn(email: String, password: String, adminOnly: Boolean = false) {
        if (email.isBlank() || password.isBlank()) {
            _notice.value = AuthNotice.CREDENTIALS_REQUIRED
            return
        }
        val auth = authRepository ?: return
        val profiles = profileRepository ?: return
        viewModelScope.launch {
            operationMutex.withLock {
                _isBusy.value = true
                _notice.value = null
                adminRoleRequiredForPendingSignIn = adminOnly
                try {
                    try {
                        auth.signIn(email.trim(), password)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        adminRoleRequiredForPendingSignIn = false
                        _uiState.value = AppUiState.SignedOut
                        _notice.value = AuthNotice.SIGN_IN_FAILED
                        return@withLock
                    }

                    try {
                        val userId = auth.currentUserId()
                            ?: throw IllegalStateException("Authenticated session has no user id")
                        val profile = profiles.loadMyProfileOrAcceptInvitation()
                        if (profile.userId != userId) {
                            throw IllegalStateException("Profile identity did not match the session")
                        }
                        if (profile.accountStatus != AccountStatus.ACTIVE) {
                            auth.signOutLocallyAndRemotely()
                            adminRoleRequiredForPendingSignIn = false
                            _uiState.value = AppUiState.SignedOut
                            _notice.value = AuthNotice.ACCOUNT_NOT_ACTIVE
                            return@withLock
                        }
                        if (adminOnly && !profile.role.canEnterAdminExperience) {
                            auth.signOutLocallyAndRemotely()
                            adminRoleRequiredForPendingSignIn = false
                            _uiState.value = AppUiState.SignedOut
                            _notice.value = AuthNotice.ADMIN_ACCESS_REQUIRED
                            return@withLock
                        }
                        adminRoleRequiredForPendingSignIn = false
                        _uiState.value = AppUiState.Authorized(profile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Authentication alone is insufficient. No dashboard appears unless
                        // the backend returns a matching, active profile with a known role.
                        // Keep the admin-only gate through a retry if this was an admin sign-in.
                        _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
                    }
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun requestPasswordReset(email: String) {
        if (email.isBlank() || !email.contains('@')) {
            _notice.value = AuthNotice.CREDENTIALS_REQUIRED
            return
        }
        val auth = authRepository ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            try {
                auth.requestPasswordReset(email.trim())
                // Keep the response generic so the UI does not disclose account existence.
                _notice.value = AuthNotice.RECOVERY_REQUESTED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _notice.value = AuthNotice.RECOVERY_FAILED
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updatePassword(newPassword: String, confirmation: String) {
        if (newPassword != confirmation) {
            _notice.value = AuthNotice.PASSWORD_MISMATCH
            return
        }
        if (newPassword.length < 8) {
            _notice.value = AuthNotice.PASSWORD_TOO_SHORT
            return
        }
        val auth = authRepository ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            try {
                auth.updatePassword(newPassword)
                onPasswordRecoveryComplete()
                _recoveryPending.value = false
                _notice.value = AuthNotice.PASSWORD_UPDATED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _notice.value = AuthNotice.PASSWORD_UPDATE_FAILED
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun saveProfile(fullName: String, phoneNumber: String) {
        if (!ProfileValidator.validateName(fullName)) {
            _notice.value = AuthNotice.PROFILE_NAME_INVALID
            return
        }
        if (!ProfileValidator.validatePhone(phoneNumber)) {
            _notice.value = AuthNotice.PROFILE_PHONE_INVALID
            return
        }
        val profiles = profileRepository ?: return
        viewModelScope.launch {
            operationMutex.withLock {
                val current = _uiState.value as? AppUiState.Authorized ?: return@withLock
                _isBusy.value = true
                _notice.value = null
                try {
                    val updated = profiles.updateMyPermittedFields(
                        fullName = fullName.trim(),
                        phoneNumber = phoneNumber.trim().takeIf(String::isNotEmpty),
                    )
                    if (updated.userId != current.profile.userId || updated.role != current.profile.role) {
                        throw IllegalStateException("The backend returned an unexpected profile")
                    }
                    _uiState.value = AppUiState.Authorized(updated)
                    _notice.value = AuthNotice.PROFILE_SAVED
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _notice.value = AuthNotice.PROFILE_SAVE_FAILED
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun retryProfileLoad() {
        val auth = authRepository ?: return
        val profiles = profileRepository ?: return
        viewModelScope.launch {
            operationMutex.withLock {
                _isBusy.value = true
                try {
                    val userId = auth.currentUserId()
                    if (userId == null) {
                        adminRoleRequiredForPendingSignIn = false
                        _uiState.value = AppUiState.SignedOut
                        return@withLock
                    }
                    loadAuthorizedProfile(userId, profiles)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun signOut() {
        val auth = authRepository ?: return
        viewModelScope.launch {
            operationMutex.withLock {
                _isBusy.value = true
                _notice.value = null
                adminRoleRequiredForPendingSignIn = false
                try {
                    val remoteConfirmed = auth.signOutLocallyAndRemotely()
                    _uiState.value = AppUiState.SignedOut
                    if (!remoteConfirmed) _notice.value = AuthNotice.SIGNED_OUT_LOCAL_ONLY
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A repository should clear the local session on failure. Fail closed in UI.
                    _uiState.value = AppUiState.SignedOut
                    _notice.value = AuthNotice.SIGNED_OUT_LOCAL_ONLY
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    private suspend fun handleSessionEvent(event: AuthSessionEvent) {
        operationMutex.withLock {
            when (event) {
                AuthSessionEvent.Initializing -> {
                    if (_uiState.value !is AppUiState.Authorized) {
                        _uiState.value = AppUiState.CheckingSession
                    }
                }
                AuthSessionEvent.SignedOut -> {
                    adminRoleRequiredForPendingSignIn = false
                    if (_uiState.value !is AppUiState.SetupRequired) {
                        _uiState.value = AppUiState.SignedOut
                    }
                }
                AuthSessionEvent.SessionExpired -> {
                    adminRoleRequiredForPendingSignIn = false
                    _uiState.value = AppUiState.SignedOut
                    _notice.value = AuthNotice.SESSION_EXPIRED
                }
                is AuthSessionEvent.SignedIn -> {
                    val userId = event.userId
                    if (userId == null) {
                        _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
                    } else {
                        val current = _uiState.value as? AppUiState.Authorized
                        if (current?.profile?.userId != userId) {
                            _isBusy.value = true
                            try {
                                loadAuthorizedProfile(userId, profileRepository ?: return)
                            } finally {
                                _isBusy.value = false
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadAuthorizedProfile(
        expectedUserId: String,
        profiles: MemberProfileRepository,
    ) {
        try {
            val profile = profiles.loadMyProfileOrAcceptInvitation()
            if (profile.userId != expectedUserId) {
                _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
                return
            }
            if (profile.accountStatus != AccountStatus.ACTIVE) {
                authRepository?.signOutLocallyAndRemotely()
                adminRoleRequiredForPendingSignIn = false
                _uiState.value = AppUiState.SignedOut
                _notice.value = AuthNotice.ACCOUNT_NOT_ACTIVE
                return
            }
            if (adminRoleRequiredForPendingSignIn && !profile.role.canEnterAdminExperience) {
                authRepository?.signOutLocallyAndRemotely()
                adminRoleRequiredForPendingSignIn = false
                _uiState.value = AppUiState.SignedOut
                _notice.value = AuthNotice.ADMIN_ACCESS_REQUIRED
                return
            }
            adminRoleRequiredForPendingSignIn = false
            _uiState.value = AppUiState.Authorized(profile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _uiState.value = AppUiState.ProfileUnavailable(ProfileUnavailableReason.PROFILE_LOAD_FAILED)
        }
    }

    class Factory(
        private val authRepository: AuthRepository?,
        private val profileRepository: MemberProfileRepository?,
        private val isConfigured: Boolean,
        private val passwordRecoveryPending: StateFlow<Boolean>,
        private val onPasswordRecoveryComplete: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(
                authRepository = authRepository,
                profileRepository = profileRepository,
                isConfigured = isConfigured,
                passwordRecoveryPending = passwordRecoveryPending,
                onPasswordRecoveryComplete = onPasswordRecoveryComplete,
            ) as T
        }
    }
}
