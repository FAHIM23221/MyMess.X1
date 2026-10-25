package com.smarttransparentmess.erp.domain.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedErpCommunityValidationTest {
    @Test
    fun privateMessageRequiresRecipientAndGroupDoesNot() {
        val error = runCatching {
            ExtendedErpValidation.validate(ErpAction.SEND_MESSAGE, mapOf("kind" to "PRIVATE", "body" to "Hello"))
        }.exceptionOrNull()
        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REQUIRED, (error as ExtendedErpValidationException).error)
        val group = ExtendedErpValidation.validate(ErpAction.SEND_MESSAGE, mapOf("kind" to "GROUP", "body" to "Hello"))
        assertEquals("GROUP", group.getValue("kind"))
    }

    @Test
    fun moderationAndSuggestionReasonsAreValidated() {
        val moderationError = runCatching {
            ExtendedErpValidation.validate(ErpAction.MODERATE_MESSAGE, mapOf("message_id" to "m", "status" to "HIDDEN", "reason" to "short"))
        }.exceptionOrNull()
        assertEquals(ExtendedErpValidationError.REASON, (moderationError as ExtendedErpValidationException).error)
        val suggestionError = runCatching {
            ExtendedErpValidation.validate(ErpAction.REVIEW_SUGGESTION, mapOf("suggestion_id" to "s", "decision" to "REJECTED"))
        }.exceptionOrNull()
        assertEquals(ExtendedErpValidationError.REASON, (suggestionError as ExtendedErpValidationException).error)
    }

    @Test
    fun complaintResponseRequiresSupportedStatus() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.RESPOND_COMPLAINT,
                mapOf("complaint_id" to "c", "status" to "OPEN", "response" to "A response"),
            )
        }.exceptionOrNull()
        assertEquals(ExtendedErpValidationError.SELECTION, (error as ExtendedErpValidationException).error)
    }
}
