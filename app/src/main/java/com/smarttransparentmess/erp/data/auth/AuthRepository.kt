package com.smarttransparentmess.erp.data.auth

import kotlinx.coroutines.flow.Flow

sealed interface AuthSessionEvent {
    data object Initializing : AuthSessionEvent
    data object SignedOut : AuthSessionEvent
    data object SessionExpired : AuthSessionEvent
    data class SignedIn(val userId: String?) : AuthSessionEvent
}

interface AuthRepository {
    val sessionEvents: Flow<AuthSessionEvent>

    suspend fun awaitReady()
    suspend fun signIn(email: String, password: String)
    suspend fun signOutLocallyAndRemotely(): Boolean
    suspend fun currentUserId(): String?
    suspend fun requestPasswordReset(email: String)
    suspend fun updatePassword(newPassword: String)
}
