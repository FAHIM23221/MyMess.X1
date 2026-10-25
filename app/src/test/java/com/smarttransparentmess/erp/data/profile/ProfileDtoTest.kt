package com.smarttransparentmess.erp.data.profile

import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileDtoTest {
    @Test
    fun mapsBackendProfileEnumsAndNullableFields() {
        val profile = ProfileDto(
            userId = "user-1",
            messId = "mess-1",
            memberId = null,
            fullName = "A Member",
            phoneNumber = null,
            roomNumber = null,
            seatNumber = null,
            joinDate = null,
            role = "MAIN_ADMIN",
            accountStatus = "ACTIVE",
            isCurrentManager = false,
            avatarPath = null,
        ).toDomain()

        assertEquals(AppRole.MAIN_ADMIN, profile.role)
        assertEquals(AccountStatus.ACTIVE, profile.accountStatus)
        assertNull(profile.memberId)
        assertNull(profile.phoneNumber)
    }

    @Test
    fun unsupportedBackendRoleIsRejected() {
        val dto = ProfileDto(
            userId = "user-1",
            messId = "mess-1",
            fullName = "A Member",
            role = "SUPERUSER",
            accountStatus = "ACTIVE",
        )
        assertThrows(IllegalStateException::class.java) { dto.toDomain() }
    }
}
