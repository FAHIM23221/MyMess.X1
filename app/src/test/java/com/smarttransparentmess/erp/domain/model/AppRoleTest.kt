package com.smarttransparentmess.erp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRoleTest {
    @Test
    fun databaseRolesAreParsedCaseInsensitivelyAndUnknownRolesFailClosed() {
        assertEquals(AppRole.MEMBER, AppRole.fromDatabase(" member "))
        assertEquals(AppRole.ADMIN, AppRole.fromDatabase("Admin"))
        assertEquals(AppRole.MAIN_ADMIN, AppRole.fromDatabase("MAIN_ADMIN"))
        assertNull(AppRole.fromDatabase("OWNER"))
        assertNull(AppRole.fromDatabase(""))
    }

    @Test
    fun onlyBackendAdministratorRolesEnterAdminExperience() {
        assertFalse(AppRole.MEMBER.canEnterAdminExperience)
        assertTrue(AppRole.ADMIN.canEnterAdminExperience)
        assertTrue(AppRole.MAIN_ADMIN.canEnterAdminExperience)
    }
}
