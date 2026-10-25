package com.smarttransparentmess.erp

import android.app.Application
import com.smarttransparentmess.erp.core.config.SupabaseConfig
import com.smarttransparentmess.erp.data.auth.SupabaseAuthRepository
import com.smarttransparentmess.erp.data.profile.SupabaseMemberProfileRepository
import com.smarttransparentmess.erp.data.security.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartMessApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val config: SupabaseConfig = SupabaseConfig.fromBuildConfig()
    val supabaseClient: SupabaseClient? = if (config.isConfigured) {
        runCatching { SupabaseClientProvider.create(application, config) }.getOrNull()
    } else {
        null
    }

    val authRepository = supabaseClient?.let {
        SupabaseAuthRepository(it, SupabaseClientProvider.AUTH_REDIRECT_URL)
    }
    val profileRepository = supabaseClient?.let(::SupabaseMemberProfileRepository)

    private val _passwordRecoveryPending = MutableStateFlow(false)
    val passwordRecoveryPending: StateFlow<Boolean> = _passwordRecoveryPending.asStateFlow()

    fun markPasswordRecoveryComplete() {
        _passwordRecoveryPending.value = false
    }

    fun handleAuthIntent(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val isSupabaseAuthCallback =
            uri.scheme == SupabaseClientProvider.AUTH_SCHEME &&
                uri.host == SupabaseClientProvider.AUTH_HOST
        if (!isSupabaseAuthCallback) return

        // In Phase 1 password recovery is the only configured auth deep link.
        if (uri.getQueryParameter("code") != null || uri.getQueryParameter("type") == "recovery") {
            _passwordRecoveryPending.value = true
        }
        supabaseClient?.handleDeeplinks(intent)
    }
}
