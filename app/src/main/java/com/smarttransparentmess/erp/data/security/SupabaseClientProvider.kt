package com.smarttransparentmess.erp.data.security

import android.content.Context
import com.smarttransparentmess.erp.core.config.SupabaseConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {
    const val AUTH_SCHEME = "smarttransparentmess"
    const val AUTH_HOST = "auth"
    const val AUTH_REDIRECT_URL = "$AUTH_SCHEME://$AUTH_HOST"

    fun create(context: Context, config: SupabaseConfig): SupabaseClient {
        require(config.isConfigured) { "Supabase project URL and publishable key are required" }
        val secureStorage = AndroidEncryptedAuthStorage(context.applicationContext)
        return createSupabaseClient(
            supabaseUrl = config.normalizedProjectUrl,
            supabaseKey = config.publishableKey.trim(),
        ) {
            // supabase-kt 3.8.0 has a code path that can include an access token in an
            // error log. Keep SDK logging disabled; never enable verbose auth logging in production.
            defaultLogLevel = LogLevel.NONE
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = AUTH_SCHEME
                host = AUTH_HOST
                sessionManager = secureStorage
                codeVerifierCache = secureStorage
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            install(Postgrest)
            install(Functions) {
                // Never fall back to the publishable key for an authenticated mutation.
                requireValidSession = true
            }
        }
    }
}
