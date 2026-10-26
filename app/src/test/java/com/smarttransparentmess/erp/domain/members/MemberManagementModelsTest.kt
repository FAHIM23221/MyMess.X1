package com.smarttransparentmess.erp.domain.members

import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberManagementModelsTest {
    private val activeMember = ManagedMember(
        userId = "member-1",
        fullName = "Ayaan",
        email = "ayaan@example.test",
        phoneNumber = "+8801000000000",
        memberId = "M-001",
        role = AppRole.MEMBER,
        effectiveRole = AppRole.MEMBER,
        accountStatus = AccountStatus.ACTIVE,
        isCurrentManager = false,
        joinDate = "2026-01-01",
        departedAt = null,
        departureReason = null,
        createdAt = null,
        updatedAt = null,
    )

    @Test
    fun departedStatusIsDerivedWithoutDeletingAccountStatus() {
        val departed = activeMember.copy(
            accountStatus = AccountStatus.SUSPENDED,
            departedAt = "2026-10-25T00:00:00Z",
            departureReason = "Moved out",
        )
        assertEquals(ManagedLifecycleStatus.DEPARTED, departed.lifecycleStatus)
        assertTrue(departed.isDeparted)
        assertEquals(AccountStatus.SUSPENDED, departed.accountStatus)
    }

    @Test
    fun capacityCountsCurrentAndPendingButNotDepartedOrCancelled() {
        val departed = activeMember.copy(userId = "departed", departedAt = "2026-10-25T00:00:00Z")
        val admin = activeMember.copy(
            userId = "admin",
            role = AppRole.ADMIN,
            effectiveRole = AppRole.ADMIN,
        )
        val mainAdmin = activeMember.copy(
            userId = "main",
            role = AppRole.MEMBER,
            effectiveRole = AppRole.MAIN_ADMIN,
        )
        val pending = invitation("pending", InvitationStatus.PENDING, AppRole.ADMIN)
        val cancelled = invitation("cancelled", InvitationStatus.CANCELLED, AppRole.ADMIN)
        val capacity = MemberCapacity.from(
            MemberManagementSnapshot(
                members = listOf(activeMember, departed, admin, mainAdmin),
                invitations = listOf(pending, cancelled),
                auditEvents = emptyList(),
                loadedAt = "2026-10-25T00:00:00Z",
            ),
        )
        assertEquals(3, capacity.currentMembers)
        assertEquals(1, capacity.pendingMembers)
        assertEquals(4, capacity.totalMembers)
        assertEquals(1, capacity.currentAdmins)
        assertEquals(1, capacity.pendingAdmins)
        assertEquals(2, capacity.totalAdmins)
        assertEquals(1, capacity.activeMainAdmins)
        assertTrue(capacity.canAddMember)
        assertTrue(capacity.canAddAdmin)
    }

    @Test
    fun capacityRejectsTwentyFirstMemberAndSixthAdmin() {
        val members = (1..20).map { activeMember.copy(userId = "u$it", memberId = "M$it") }
        val atMemberCapacity = MemberCapacity.from(
            MemberManagementSnapshot(members, emptyList(), emptyList(), "now"),
        )
        assertFalse(atMemberCapacity.canAddMember)
        assertFalse(atMemberCapacity.canAddAdmin)

        val admins = (1..5).map {
            activeMember.copy(userId = "a$it", role = AppRole.ADMIN, effectiveRole = AppRole.ADMIN)
        }
        val atAdminCapacity = MemberCapacity.from(
            MemberManagementSnapshot(admins, emptyList(), emptyList(), "now"),
        )
        assertTrue(atAdminCapacity.canAddMember)
        assertFalse(atAdminCapacity.canAddAdmin)
    }

    @Test
    fun searchFiltersAndSortAreAppliedTogether() {
        val other = activeMember.copy(
            userId = "member-2",
            fullName = "Bina",
            email = "bina@example.test",
            memberId = "M-002",
            accountStatus = AccountStatus.SUSPENDED,
            joinDate = "2026-02-01",
        )
        val result = filterAndSortMembers(
            members = listOf(other, activeMember),
            query = "M-00",
            status = MemberStatusFilter.ALL,
            role = MemberRoleFilter.MEMBER,
            sort = MemberSort.MEMBER_ID,
        )
        assertEquals(listOf("M-001", "M-002"), result.map { it.memberId })
        assertEquals(
            listOf("member-2"),
            filterAndSortMembers(
                members = listOf(other, activeMember),
                query = "bina",
                status = MemberStatusFilter.SUSPENDED,
                role = MemberRoleFilter.MEMBER,
                sort = MemberSort.NAME,
            ).map { it.userId },
        )
    }

    private fun invitation(id: String, status: InvitationStatus, role: AppRole) = MemberInvitation(
        id = id,
        email = "$id@example.test",
        fullName = id,
        phoneNumber = "+8801000000000",
        memberId = id,
        joinDate = "2026-01-01",
        intendedRole = role,
        status = status,
        deliveryStatus = InvitationDeliveryStatus.PENDING,
        deliveryAttempts = 0,
        lastSentAt = null,
        lastDeliveryAttemptAt = null,
        lastDeliveryError = null,
        acceptedAt = null,
        createdAt = "2026-01-01T00:00:00Z",
    )
}
