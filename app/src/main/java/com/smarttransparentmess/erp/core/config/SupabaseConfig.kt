package com.smarttransparentmess.erp.core.config

import com.smarttransparentmess.erp.BuildConfig
import java.net.URI

data class SupabaseConfig(
    val projectUrl: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean
        get() {
            if (projectUrl.isBlank() || publishableKey.isBlank()) return false
            return runCatching {
                val uri = URI(projectUrl.trim())
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }

    val normalizedProjectUrl: String
        get() = projectUrl.trim().trimEnd('/')

    companion object {
        fun fromBuildConfig(): SupabaseConfig = SupabaseConfig(
            projectUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }
}
