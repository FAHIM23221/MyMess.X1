package com.smarttransparentmess.erp.data.members

import com.smarttransparentmess.erp.domain.members.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingResponseParserTest {
    @Test
    fun sentResponseDoesNotClaimADeliveryFailure() {
        val result = OnboardingResponseParser.parse(
            """{"status":"INVITATION_SENT","invitation_id":"8e4f0d36-3e40-4b1f-b4de-f87d0e46f809"}""",
            200,
        )

        assertEquals(OnboardingStatus.INVITATION_SENT, result.status)
        assertEquals("8e4f0d36-3e40-4b1f-b4de-f87d0e46f809", result.invitationId)
        assertNull(result.dependency)
    }

    @Test
    fun structuredConfigurationErrorSurvivesNon2xxHandling() {
        val result = OnboardingResponseParser.parse(
            """{"status":"SERVER_CONFIGURATION_MISSING","dependency":"EDGE_AUTH_ADMIN_RUNTIME_ENVIRONMENT"}""",
            503,
        )

        assertEquals(OnboardingStatus.SERVER_CONFIGURATION_MISSING, result.status)
        assertEquals("SERVER_CONFIGURATION_MISSING", result.code)
        assertEquals("EDGE_AUTH_ADMIN_RUNTIME_ENVIRONMENT", result.dependency)
    }

    @Test
    fun gatewayAuthenticationErrorIsNotMislabeledAsSmtpDelivery() {
        val result = OnboardingResponseParser.parse(
            """{"message":"Invalid JWT"}""",
            401,
        )

        assertEquals(OnboardingStatus.AUTHENTICATION_REQUIRED, result.status)
        assertEquals("FUNCTION_HTTP_401", result.code)
        assertEquals("SUPABASE_AUTH_SESSION", result.dependency)
    }

    @Test
    fun businessRejectionKeepsItsSafeCodeWithoutSmtpAssumption() {
        val result = OnboardingResponseParser.parse(
            """{"status":"REQUEST_REJECTED","code":"AUTHORIZATION_VALIDATION_CAPACITY_OR_DUPLICATE"}""",
            200,
        )

        assertEquals(OnboardingStatus.REQUEST_REJECTED, result.status)
        assertEquals("AUTHORIZATION_VALIDATION_CAPACITY_OR_DUPLICATE", result.code)
        assertNull(result.dependency)
    }

    @Test
    fun explicitAuthDeliveryFailureKeepsItsActualDependency() {
        val result = OnboardingResponseParser.parse(
            """{"status":"DELIVERY_FAILED","code":"AUTH_INVITE_REJECTED","dependency":"SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP"}""",
            200,
        )

        assertEquals(OnboardingStatus.DELIVERY_FAILED, result.status)
        assertEquals("AUTH_INVITE_REJECTED", result.code)
        assertEquals("SUPABASE_AUTH_INVITE_AND_CONFIGURED_SMTP", result.dependency)
    }

    @Test
    fun edgeCrashEnvelopeIsRequestUnconfirmedNotDeliveryUnconfirmed() {
        val result = OnboardingResponseParser.parse(
            """{"status":"REQUEST_UNCONFIRMED","code":"EDGE_OPERATION_UNCONFIRMED","dependency":"MEMBER_INVITATION_FUNCTION"}""",
            500,
        )

        assertEquals(OnboardingStatus.REQUEST_UNCONFIRMED, result.status)
        assertEquals("EDGE_OPERATION_UNCONFIRMED", result.code)
        assertEquals("MEMBER_INVITATION_FUNCTION", result.dependency)
    }

    @Test
    fun malformedSuccessResponseIsUnconfirmedAndRawBodyIsNotReturned() {
        val raw = "provider secret@example.test and private-provider-detail"
        val result = OnboardingResponseParser.parse(raw, 200)

        assertEquals(OnboardingStatus.REQUEST_UNCONFIRMED, result.status)
        assertEquals("UNRECOGNIZED_BACKEND_RESPONSE", result.code)
        assertFalse(result.code.orEmpty().contains("secret@"))
        assertFalse(result.dependency.orEmpty().contains("private-provider"))
    }

    @Test
    fun unsafeDetailFieldsAreReplacedWithSafeDefaults() {
        val result = OnboardingResponseParser.parse(
            """{"status":"SERVER_CONFIGURATION_MISSING","code":"secret@example.test","dependency":"smtp-password-not-for-ui"}""",
            503,
        )

        assertEquals("SERVER_CONFIGURATION_MISSING", result.code)
        assertEquals("MEMBER_INVITATION_FUNCTION", result.dependency)
    }

    @Test
    fun unknownNon2xxResponseUsesHttpCodeWithoutLeakingBody() {
        val result = OnboardingResponseParser.parse(
            """{"status":"UNRECOGNIZED","message":"private provider detail"}""",
            503,
        )

        assertEquals(OnboardingStatus.REQUEST_FAILED, result.status)
        assertEquals("FUNCTION_HTTP_503", result.code)
        assertEquals("MEMBER_INVITATION_FUNCTION", result.dependency)
    }
}
