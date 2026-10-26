package com.smarttransparentmess.erp.domain.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedErpDutiesValidationTest {
    @Test
    fun swapRequiresTargetAndReason() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.REQUEST_DUTY_CHANGE,
                mapOf("duty_id" to "d", "kind" to "SWAP", "reason" to "A valid swap reason"),
            )
        }.exceptionOrNull()
        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REQUIRED, (error as ExtendedErpValidationException).error)
    }

    @Test
    fun scheduledNoticeRequiresTimestamp() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.SAVE_NOTICE,
                mapOf("title" to "Notice", "body" to "Notice body", "audience" to "ALL", "status" to "SCHEDULED"),
            )
        }.exceptionOrNull()
        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REQUIRED, (error as ExtendedErpValidationException).error)
        val valid = ExtendedErpValidation.validate(
            ErpAction.SAVE_NOTICE,
            mapOf("title" to "Notice", "body" to "Notice body", "audience" to "ALL", "status" to "PUBLISHED"),
        )
        assertEquals("PUBLISHED", valid.getValue("status"))
    }
}
