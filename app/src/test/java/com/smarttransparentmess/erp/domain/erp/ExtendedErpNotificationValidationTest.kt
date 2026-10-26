package com.smarttransparentmess.erp.domain.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedErpNotificationValidationTest {
    @Test
    fun specificMemberAudienceRequiresRecipient() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.SEND_MEMBER_NOTIFICATION,
                mapOf("audience" to "MEMBER", "title" to "Meal update", "body" to "Lunch starts at 2 PM."),
            )
        }.exceptionOrNull()

        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REQUIRED, (error as ExtendedErpValidationException).error)
    }

    @Test
    fun memberNotificationTrimsFieldsAndKeepsRecipient() {
        val valid = ExtendedErpValidation.validate(
            ErpAction.SEND_MEMBER_NOTIFICATION,
            mapOf(
                "audience" to " MEMBER ",
                "member_id" to " member-id ",
                "title" to " Meal update ",
                "body" to " Lunch starts at 2 PM. ",
            ),
        )

        assertEquals("MEMBER", valid.getValue("audience"))
        assertEquals("member-id", valid.getValue("member_id"))
        assertEquals("Meal update", valid.getValue("title"))
        assertEquals("Lunch starts at 2 PM.", valid.getValue("body"))
    }

    @Test
    fun allAndAdminAudiencesDoNotRequireMember() {
        listOf("ALL", "ADMINS").forEach { audience ->
            val valid = ExtendedErpValidation.validate(
                ErpAction.SEND_MEMBER_NOTIFICATION,
                mapOf(
                    "audience" to audience,
                    "member_id" to "stale-hidden-selection",
                    "title" to "Meeting",
                    "body" to "Please join at 8 PM.",
                ),
            )
            assertEquals(audience, valid.getValue("audience"))
            assertFalse(valid.containsKey("member_id"))
        }
    }

    @Test
    fun unsupportedAudienceAndShortBodyAreRejected() {
        val audienceError = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.SEND_MEMBER_NOTIFICATION,
                mapOf("audience" to "EVERYONE", "title" to "Meeting", "body" to "Please join."),
            )
        }.exceptionOrNull()
        assertTrue(audienceError is ExtendedErpValidationException)
        assertEquals(
            ExtendedErpValidationError.SELECTION,
            (audienceError as ExtendedErpValidationException).error,
        )

        val bodyError = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.SEND_MEMBER_NOTIFICATION,
                mapOf("audience" to "ALL", "title" to "Meeting", "body" to "x"),
            )
        }.exceptionOrNull()
        assertTrue(bodyError is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.REQUIRED, (bodyError as ExtendedErpValidationException).error)
    }
}
