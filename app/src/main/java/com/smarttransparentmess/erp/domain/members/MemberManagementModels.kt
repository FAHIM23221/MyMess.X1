package com.smarttransparentmess.erp.domain.members

import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole

enum class ManagedLifecycleStatus { ACTIVE, PENDING, SUSPENDED, DEPARTED }

enum class MemberStatusFilter { ALL, ACTIVE, PENDING, SUSPENDED, DEPARTED }

enum class MemberRoleFilter { ALL, MEMBER, ADMIN, MAIN_ADMIN }

enum class MemberSort { NAME, MEMBER_ID, JOIN_DATE, STATUS }

enum class InvitationStatus { PENDING, ACCEPTED, CANCELLED }

enum class InvitationDeliveryStatus { PENDING, SENT, FAILED, UNCONFIRMED, NOT_REQUIRED }

data class ManagedMember(
    val userId: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val memberId: String?,
    val role: AppRole,
    val effectiveRole: AppRole,
    val accountStatus: AccountStatus,
    val isCurrentManager: Boolean,
    val joinDate: String?,
    val departedAt: String?,
    val departureReason: String?,
    val createdAt: String?,
    val updatedAt: String?,
) {
    val lifecycleStatus: ManagedLifecycleStatus
        get() = if (departedAt != null) {
            ManagedLifecycleStatus.DEPARTED
        } else {
            when (accountStatus) {
                AccountStatus.ACTIVE -> ManagedLifecycleStatus.ACTIVE
                AccountStatus.PENDING -> ManagedLifecycleStatus.PENDING
                AccountStatus.SUSPENDED -> ManagedLifecycleStatus.SUSPENDED
            }
        }

    val isDeparted: Boolean get() = departedAt != null
}

data class MemberInvitation(
    val id: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String,
    val memberId: String,
    val joinDate: String,
    val intendedRole: AppRole,
    val status: InvitationStatus,
    val deliveryStatus: InvitationDeliveryStatus,
    val deliveryAttempts: Int,
    val lastSentAt: String?,
    val lastDeliveryAttemptAt: String?,
    val lastDeliveryError: String?,
    val acceptedAt: String?,
    val createdAt: String,
)

data class MemberAuditEvent(
    val id: String,
    val actorUserId: String?,
    val targetUserId: String?,
    val actorType: String,
    val action: String,
    val reason: String?,
    val details: String,
    val createdAt: String,
)

data class MemberManagementSnapshot(
    val members: List<ManagedMember>,
    val invitations: List<MemberInvitation>,
    val auditEvents: List<MemberAuditEvent>,
    val loadedAt: String,
)

data class MemberCapacity(
    val currentMembers: Int,
    val pendingMembers: Int,
    val currentAdmins: Int,
    val pendingAdmins: Int,
    val activeMainAdmins: Int,
) {
    val totalMembers: Int get() = currentMembers + pendingMembers
    val totalAdmins: Int get() = currentAdmins + pendingAdmins
    val canAddMember: Boolean get() = totalMembers < MAX_MEMBERS
    val canAddAdmin: Boolean get() = canAddMember && totalAdmins < MAX_ADMINS

    companion object {
        const val MAX_MEMBERS = 20
        const val MAX_ADMINS = 5

        fun from(snapshot: MemberManagementSnapshot): MemberCapacity = MemberCapacity(
            currentMembers = snapshot.members.count { !it.isDeparted },
            pendingMembers = snapshot.invitations.count { it.status == InvitationStatus.PENDING },
            currentAdmins = snapshot.members.count {
                !it.isDeparted && it.effectiveRole == AppRole.ADMIN
            },
            pendingAdmins = snapshot.invitations.count {
                it.status == InvitationStatus.PENDING && it.intendedRole == AppRole.ADMIN
            },
            activeMainAdmins = snapshot.members.count {
                !it.isDeparted && it.effectiveRole == AppRole.MAIN_ADMIN &&
                    it.accountStatus == AccountStatus.ACTIVE
            },
        )
    }
}

fun filterAndSortMembers(
    members: List<ManagedMember>,
    query: String,
    status: MemberStatusFilter,
    role: MemberRoleFilter,
    sort: MemberSort,
): List<ManagedMember> {
    val normalizedQuery = query.trim().lowercase()
    return members.asSequence()
        .filter { member ->
            normalizedQuery.isEmpty() || listOf(
                member.fullName, member.email, member.phoneNumber, member.memberId.orEmpty(),
            ).any { it.lowercase().contains(normalizedQuery) }
        }
        .filter { member -> status == MemberStatusFilter.ALL || member.lifecycleStatus.name == status.name }
        .filter { member -> role == MemberRoleFilter.ALL || member.effectiveRole.name == role.name }
        .sortedWith(
            when (sort) {
                MemberSort.NAME -> compareBy<ManagedMember> { it.fullName.lowercase() }
                    .thenBy { it.memberId.orEmpty() }
                MemberSort.MEMBER_ID -> compareBy<ManagedMember> { it.memberId.orEmpty() }
                    .thenBy { it.fullName.lowercase() }
                MemberSort.JOIN_DATE -> compareByDescending<ManagedMember> { it.joinDate.orEmpty() }
                    .thenBy { it.fullName.lowercase() }
                MemberSort.STATUS -> compareBy<ManagedMember> { it.lifecycleStatus.name }
                    .thenBy { it.fullName.lowercase() }
            },
        )
        .toList()
}

data class NewMemberInput(
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val memberId: String,
    val joinDate: String,
    val intendedRole: AppRole,
)

data class MemberProfileEditInput(
    val fullName: String,
    val phoneNumber: String,
    val memberId: String,
    val joinDate: String,
)

enum class OnboardingStatus {
    ACTIVE,
    INVITATION_SENT,
    DELIVERY_FAILED,
    DELIVERY_UNCONFIRMED,
    REQUEST_REJECTED,
    AUTHENTICATION_REQUIRED,
    SERVER_CONFIGURATION_MISSING,
    REQUEST_FAILED,
    REQUEST_UNCONFIRMED,
}

data class OnboardingResult(
    val status: OnboardingStatus,
    val invitationId: String? = null,
    val code: String? = null,
    val dependency: String? = null,
)
