package com.smarttransparentmess.erp.domain.validation

object ProfileValidator {
    fun validateName(value: String): Boolean {
        val normalized = value.trim()
        return normalized.length in 2..80
    }

    /**
     * Accepts common international phone punctuation without requiring one country's format.
     * Blank is permitted because phone is optional during Phase 1 provisioning.
     */
    fun validatePhone(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return true
        if (normalized.length !in 7..24) return false
        if (normalized.any { !(isPhoneDigit(it) || it in "+-() ") }) return false
        return normalized.count(::isPhoneDigit) >= 7
    }

    private fun isPhoneDigit(char: Char): Boolean =
        char in '0'..'9' || char in '\u09E6'..'\u09EF'
}
