package com.smarttransparentmess.erp.domain.model

/**
 * UI role used after the active profile is fetched from the backend.
 * This is never an authorization source: protected requests are governed by Supabase RLS.
 */
enum class AppRole {
    MEMBER,
    ADMIN,
    MAIN_ADMIN;

    val canEnterAdminExperience: Boolean
        get() = this == ADMIN || this == MAIN_ADMIN

    companion object {
        fun fromDatabase(value: String): AppRole? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}

enum class AccountStatus {
    ACTIVE,
    PENDING,
    SUSPENDED;

    companion object {
        fun fromDatabase(value: String): AccountStatus? =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }
}
