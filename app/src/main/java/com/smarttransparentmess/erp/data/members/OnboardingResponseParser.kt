package com.smarttransparentmess.erp.data.members

import com.smarttransparentmess.erp.domain.members.OnboardingResult
import com.smarttransparentmess.erp.domain.members.OnboardingStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Converts the invitation function's intentionally small, safe response contract.
 * Raw provider/server bodies are never returned to the UI.
 */
internal object OnboardingResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val safeDetail = Regex("^[A-Z0-9_]{1,96}$")

    fun parse(rawBody: String, httpStatusCode: Int): OnboardingResult {
        val payload = try {
            json.parseToJsonElement(rawBody).jsonObject
        } catch (_: Exception) {
            null
        }
        val status = payload?.string("status")
        val mappedStatus = when (status) {
            "ACTIVE" -> OnboardingStatus.ACTIVE
            "INVITATION_SENT" -> OnboardingStatus.INVITATION_SENT
            "DELIVERY_FAILED" -> OnboardingStatus.DELIVERY_FAILED
            "DELIVERY_UNCONFIRMED" -> OnboardingStatus.DELIVERY_UNCONFIRMED
            "REQUEST_REJECTED" -> OnboardingStatus.REQUEST_REJECTED
            "UNAUTHORIZED" -> OnboardingStatus.AUTHENTICATION_REQUIRED
            "SERVER_CONFIGURATION_MISSING" -> OnboardingStatus.SERVER_CONFIGURATION_MISSING
            "INVALID_JSON",
            "INVALID_MEMBER_FIELDS",
            "INVALID_INVITATION_ID",
            "UNSUPPORTED_ACTION",
            "METHOD_NOT_ALLOWED",
            "PAYLOAD_TOO_LARGE",
            "OPERATION_FAILED" -> OnboardingStatus.REQUEST_FAILED
            "UNCONFIRMED_BACKEND_RESPONSE",
            "REQUEST_UNCONFIRMED" -> OnboardingStatus.REQUEST_UNCONFIRMED
            else -> null
        }
        if (mappedStatus == null) return httpFailure(httpStatusCode)

        return OnboardingResult(
            status = mappedStatus,
            invitationId = payload?.string("invitation_id"),
            code = payload?.safeDetail("code") ?: defaultCode(mappedStatus),
            dependency = payload?.safeDetail("dependency") ?: defaultDependency(mappedStatus),
        )
    }

    private fun httpFailure(httpStatusCode: Int): OnboardingResult {
        val status = when (httpStatusCode) {
            401, 403 -> OnboardingStatus.AUTHENTICATION_REQUIRED
            200 -> OnboardingStatus.REQUEST_UNCONFIRMED
            else -> OnboardingStatus.REQUEST_FAILED
        }
        return OnboardingResult(
            status = status,
            code = if (httpStatusCode == 200) {
                "UNRECOGNIZED_BACKEND_RESPONSE"
            } else {
                "FUNCTION_HTTP_$httpStatusCode"
            },
            dependency = defaultDependency(status),
        )
    }

    private fun defaultCode(status: OnboardingStatus): String? = when (status) {
        OnboardingStatus.AUTHENTICATION_REQUIRED -> "AUTHENTICATED_SESSION_REQUIRED"
        OnboardingStatus.SERVER_CONFIGURATION_MISSING -> "SERVER_CONFIGURATION_MISSING"
        OnboardingStatus.REQUEST_FAILED -> "INVITATION_FUNCTION_REQUEST_FAILED"
        OnboardingStatus.REQUEST_UNCONFIRMED -> "INVITATION_REQUEST_UNCONFIRMED"
        else -> null
    }

    private fun defaultDependency(status: OnboardingStatus): String? = when (status) {
        OnboardingStatus.AUTHENTICATION_REQUIRED -> "SUPABASE_AUTH_SESSION"
        OnboardingStatus.SERVER_CONFIGURATION_MISSING,
        OnboardingStatus.REQUEST_FAILED,
        OnboardingStatus.REQUEST_UNCONFIRMED -> "MEMBER_INVITATION_FUNCTION"
        else -> null
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.safeDetail(key: String): String? =
        string(key)?.takeIf(safeDetail::matches)
}
