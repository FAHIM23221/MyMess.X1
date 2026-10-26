package com.smarttransparentmess.erp.data.members

import com.smarttransparentmess.erp.domain.members.MemberManagementSnapshot
import com.smarttransparentmess.erp.domain.members.MemberProfileEditInput
import com.smarttransparentmess.erp.domain.members.NewMemberInput
import com.smarttransparentmess.erp.domain.members.OnboardingResult
import com.smarttransparentmess.erp.domain.members.OnboardingStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface MemberManagementRepository {
    suspend fun loadSnapshot(): MemberManagementSnapshot
    suspend fun requestOnboarding(entry: NewMemberInput, clientRequestId: String): OnboardingResult
    suspend fun resendInvitation(invitationId: String): OnboardingResult
    suspend fun cancelInvitation(invitationId: String)
    suspend fun updateMember(targetUserId: String, entry: MemberProfileEditInput)
    suspend fun setLifecycle(targetUserId: String, status: String, reason: String)
    suspend fun setRole(targetUserId: String, role: AppRole, reason: String)
    suspend fun transferMainAdmin(targetUserId: String, reason: String)
}

class SupabaseMemberManagementRepository(
    private val client: SupabaseClient,
) : MemberManagementRepository {
    override suspend fun loadSnapshot(): MemberManagementSnapshot {
        requireSession()
        val members = client.postgrest.rpc("list_member_management_profiles")
            .decodeList<ManagedMemberDto>()
            .map(ManagedMemberDto::toDomain)
        val invitations = client.from("member_invitations").select()
            .decodeList<MemberInvitationDto>()
            .map(MemberInvitationDto::toDomain)
            .sortedByDescending { it.createdAt }
        val auditEvents = client.from("audit_logs").select()
            .decodeList<AuditLogDto>()
            .map(AuditLogDto::toDomain)
            .sortedByDescending { it.createdAt }
            .take(200)
        return MemberManagementSnapshot(
            members = members,
            invitations = invitations,
            auditEvents = auditEvents,
            loadedAt = Instant.now().toString(),
        )
    }

    override suspend fun requestOnboarding(
        entry: NewMemberInput,
        clientRequestId: String,
    ): OnboardingResult {
        return invokeOnboarding(
            buildJsonObject {
                put("action", JsonPrimitive("create"))
                put("full_name", JsonPrimitive(entry.fullName))
                put("email", JsonPrimitive(entry.email))
                put("phone_number", JsonPrimitive(entry.phoneNumber))
                put("member_id", JsonPrimitive(entry.memberId))
                put("join_date", JsonPrimitive(entry.joinDate))
                put("intended_role", JsonPrimitive(entry.intendedRole.name))
                put("client_request_id", JsonPrimitive(clientRequestId))
            },
        )
    }

    override suspend fun resendInvitation(invitationId: String): OnboardingResult {
        return invokeOnboarding(
            buildJsonObject {
                put("action", JsonPrimitive("resend"))
                put("invitation_id", JsonPrimitive(invitationId))
            },
        )
    }

    override suspend fun cancelInvitation(invitationId: String) {
        requireSession()
        client.postgrest.rpc(
            "cancel_member_invitation",
            buildJsonObject { put("target_invitation_id", JsonPrimitive(invitationId)) },
        )
    }

    override suspend fun updateMember(targetUserId: String, entry: MemberProfileEditInput) {
        requireSession()
        client.postgrest.rpc(
            "update_managed_member_profile",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(targetUserId))
                put("full_name", JsonPrimitive(entry.fullName))
                put("phone_number", JsonPrimitive(entry.phoneNumber))
                put("member_id", JsonPrimitive(entry.memberId))
                put("join_date", JsonPrimitive(entry.joinDate))
            },
        )
    }

    override suspend fun setLifecycle(targetUserId: String, status: String, reason: String) {
        requireSession()
        client.postgrest.rpc(
            "set_managed_member_lifecycle",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(targetUserId))
                put("new_status", JsonPrimitive(status))
                put("reason", JsonPrimitive(reason))
            },
        )
    }

    override suspend fun setRole(targetUserId: String, role: AppRole, reason: String) {
        require(role == AppRole.MEMBER || role == AppRole.ADMIN) { "Main Admin uses transfer" }
        requireSession()
        client.postgrest.rpc(
            "set_profile_role",
            buildJsonObject {
                put("target_user_id", JsonPrimitive(targetUserId))
                put("new_role", JsonPrimitive(role.name))
                put("change_reason", JsonPrimitive(reason))
            },
        )
    }

    override suspend fun transferMainAdmin(targetUserId: String, reason: String) {
        requireSession()
        client.postgrest.rpc(
            "transfer_main_admin",
            buildJsonObject {
                put("new_main_admin_user_id", JsonPrimitive(targetUserId))
                put("transfer_reason", JsonPrimitive(reason))
            },
        )
    }

    private suspend fun invokeOnboarding(body: JsonObject): OnboardingResult {
        if (client.auth.currentUserOrNull() == null) {
            return OnboardingResult(
                status = OnboardingStatus.AUTHENTICATION_REQUIRED,
                code = "AUTHENTICATED_SESSION_REQUIRED",
                dependency = "SUPABASE_AUTH_SESSION",
            )
        }
        return try {
            val response = client.functions.invoke(
                function = "member-invitation",
                body = body,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                },
            )
            OnboardingResponseParser.parse(response.bodyAsText(), response.status.value)
        } catch (error: RestException) {
            // supabase-kt throws for non-2xx responses; its error property contains
            // the function's safe JSON envelope, not a credential to display or log.
            OnboardingResponseParser.parse(error.error, error.statusCode)
        }
    }

    private fun requireSession() {
        check(client.auth.currentUserOrNull() != null) { "An authenticated session is required." }
    }
}
