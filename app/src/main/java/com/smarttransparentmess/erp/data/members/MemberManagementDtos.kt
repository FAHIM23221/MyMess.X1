package com.smarttransparentmess.erp.data.members

import com.smarttransparentmess.erp.domain.members.InvitationDeliveryStatus
import com.smarttransparentmess.erp.domain.members.InvitationStatus
import com.smarttransparentmess.erp.domain.members.ManagedMember
import com.smarttransparentmess.erp.domain.members.MemberAuditEvent
import com.smarttransparentmess.erp.domain.members.MemberInvitation
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ManagedMemberDto(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val email: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("member_id") val memberId: String? = null,
    val role: String,
    @SerialName("effective_role") val effectiveRole: String,
    @SerialName("account_status") val accountStatus: String,
    @SerialName("is_current_manager") val isCurrentManager: Boolean = false,
    @SerialName("join_date") val joinDate: String? = null,
    @SerialName("departed_at") val departedAt: String? = null,
    @SerialName("departure_reason") val departureReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain(): ManagedMember = ManagedMember(
        userId = userId,
        fullName = fullName,
        email = email,
        phoneNumber = phoneNumber,
        memberId = memberId,
        role = AppRole.fromDatabase(role) ?: error("Unsupported member role"),
        effectiveRole = AppRole.fromDatabase(effectiveRole) ?: error("Unsupported effective role"),
        accountStatus = AccountStatus.fromDatabase(accountStatus) ?: error("Unsupported account status"),
        isCurrentManager = isCurrentManager,
        joinDate = joinDate,
        departedAt = departedAt,
        departureReason = departureReason,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Serializable
data class MemberInvitationDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("join_date") val joinDate: String,
    @SerialName("intended_role") val intendedRole: String,
    val status: String,
    @SerialName("delivery_status") val deliveryStatus: String,
    @SerialName("delivery_attempts") val deliveryAttempts: Int = 0,
    @SerialName("last_sent_at") val lastSentAt: String? = null,
    @SerialName("last_delivery_attempt_at") val lastDeliveryAttemptAt: String? = null,
    @SerialName("last_delivery_error") val lastDeliveryError: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): MemberInvitation = MemberInvitation(
        id = id,
        email = email,
        fullName = fullName,
        phoneNumber = phoneNumber,
        memberId = memberId,
        joinDate = joinDate,
        intendedRole = AppRole.fromDatabase(intendedRole) ?: error("Unsupported invitation role"),
        status = InvitationStatus.entries.firstOrNull { it.name == status }
            ?: error("Unsupported invitation status"),
        deliveryStatus = InvitationDeliveryStatus.entries.firstOrNull { it.name == deliveryStatus }
            ?: error("Unsupported invitation delivery status"),
        deliveryAttempts = deliveryAttempts,
        lastSentAt = lastSentAt,
        lastDeliveryAttemptAt = lastDeliveryAttemptAt,
        lastDeliveryError = lastDeliveryError,
        acceptedAt = acceptedAt,
        createdAt = createdAt,
    )
}

@Serializable
data class AuditLogDto(
    val id: String,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("actor_type") val actorType: String = "USER",
    val action: String,
    val reason: String? = null,
    val details: JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toDomain(): MemberAuditEvent = MemberAuditEvent(
        id = id,
        actorUserId = actorUserId,
        targetUserId = targetUserId,
        actorType = actorType,
        action = action,
        reason = reason,
        details = details?.toString().orEmpty(),
        createdAt = createdAt,
    )
}
