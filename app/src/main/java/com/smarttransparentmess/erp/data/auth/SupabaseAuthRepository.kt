package com.smarttransparentmess.erp.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val redirectUrl: String,
) : AuthRepository {
    override val sessionEvents: Flow<AuthSessionEvent> = client.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> AuthSessionEvent.Initializing
            is SessionStatus.Authenticated -> AuthSessionEvent.SignedIn(status.session.user?.id)
            is SessionStatus.NotAuthenticated -> AuthSessionEvent.SignedOut
            is SessionStatus.RefreshFailure -> AuthSessionEvent.SessionExpired
        }
    }

    override suspend fun awaitReady() {
        client.auth.awaitInitialization()
    }

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signOutLocallyAndRemotely(): Boolean = try {
        client.auth.signOut(SignOutScope.LOCAL)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A network failure must not leave a usable local session on a shared device.
        try {
            client.auth.clearSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The caller still fails closed and clears the signed-in UI.
        }
        false
    }

    override suspend fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun requestPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(
            email = email.trim(),
            redirectUrl = redirectUrl,
        )
    }

    override suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser {
            password = newPassword
        }
    }
}
