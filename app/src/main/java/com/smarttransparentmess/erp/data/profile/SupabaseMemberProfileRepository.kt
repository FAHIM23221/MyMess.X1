package com.smarttransparentmess.erp.data.profile

import com.smarttransparentmess.erp.domain.model.MemberProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException

class SupabaseMemberProfileRepository(
    private val client: SupabaseClient,
) : MemberProfileRepository {
    override suspend fun loadMyProfile(): MemberProfile =
        client.from("my_profile")
            .select()
            .decodeSingle<ProfileDto>()
            .toDomain()

    override suspend fun loadMyProfileOrAcceptInvitation(): MemberProfile {
        try {
            return loadMyProfile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A newly invited Auth user has no profile until this audited RPC accepts
            // the server-held row matching the confirmed email. No role is client-supplied.
            client.postgrest.rpc("accept_pending_member_invitation")
            return loadMyProfile()
        }
    }

    override suspend fun updateMyPermittedFields(
        fullName: String,
        phoneNumber: String?,
    ): MemberProfile {
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("No authenticated user")
        client.from("profiles").update(
            ProfileUpdateDto(
                fullName = fullName.trim(),
                phoneNumber = phoneNumber?.trim()?.takeIf(String::isNotEmpty),
            ),
        ) {
            filter {
                eq("user_id", userId)
            }
        }
        return loadMyProfile()
    }
}
