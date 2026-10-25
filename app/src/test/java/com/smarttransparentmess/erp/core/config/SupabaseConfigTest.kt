package com.smarttransparentmess.erp.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {
    @Test
    fun requiresHttpsProjectUrlAndNonBlankClientKey() {
        assertTrue(SupabaseConfig("https://example.supabase.co", "sb_publishable_test").isConfigured)
        assertFalse(SupabaseConfig("http://example.supabase.co", "public-key").isConfigured)
        assertFalse(SupabaseConfig("https://example.supabase.co", " ").isConfigured)
        assertFalse(SupabaseConfig("not-a-url", "public-key").isConfigured)
    }

    @Test
    fun normalizesTrailingSlashWithoutChangingStoredValue() {
        val config = SupabaseConfig(" https://example.supabase.co/// ", "public-key")
        assertEquals("https://example.supabase.co", config.normalizedProjectUrl)
        assertTrue(config.isConfigured)
    }
}
