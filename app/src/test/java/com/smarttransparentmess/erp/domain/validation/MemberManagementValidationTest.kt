package com.smarttransparentmess.erp.domain.validation

import com.smarttransparentmess.erp.domain.model.AppRole
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberManagementValidationTest {
    private val today = LocalDate.now(ZoneId.of("Asia/Dhaka"))

    @Test
    fun newMemberNormalizesFieldsAndAcceptsSupportedRoles() {
        val result = MemberManagementValidation.newMember(
            fullName = "  Ayaan Rahman  ",
            email = "  AYAAN@EXAMPLE.TEST ",
            phoneNumber = " +8801000000000 ",
            memberId = " M-001 ",
            joinDate = today.toString(),
            intendedRole = AppRole.ADMIN,
        )
        assertEquals("Ayaan Rahman", result.fullName)
        assertEquals("ayaan@example.test", result.email)
        assertEquals("+8801000000000", result.phoneNumber)
        assertEquals("M-001", result.memberId)
        assertEquals(AppRole.ADMIN, result.intendedRole)
    }

    @Test
    fun invalidMemberFieldsHaveSpecificValidationErrors() {
        assertError(MemberManagementValidationError.NAME) {
            MemberManagementValidation.newMember("A", "a@example.test", "+8801000000000", "M1", today.toString(), AppRole.MEMBER)
        }
        assertError(MemberManagementValidationError.EMAIL) {
            MemberManagementValidation.newMember("Valid Name", "not-an-email", "+8801000000000", "M1", today.toString(), AppRole.MEMBER)
        }
        assertError(MemberManagementValidationError.PHONE) {
            MemberManagementValidation.newMember("Valid Name", "a@example.test", "abc", "M1", today.toString(), AppRole.MEMBER)
        }
        assertError(MemberManagementValidationError.MEMBER_ID) {
            MemberManagementValidation.newMember("Valid Name", "a@example.test", "+8801000000000", "", today.toString(), AppRole.MEMBER)
        }
        assertError(MemberManagementValidationError.JOIN_DATE) {
            MemberManagementValidation.newMember("Valid Name", "a@example.test", "+8801000000000", "M1", "25-10-2026", AppRole.MEMBER)
        }
        assertError(MemberManagementValidationError.ROLE) {
            MemberManagementValidation.newMember("Valid Name", "a@example.test", "+8801000000000", "M1", today.toString(), AppRole.MAIN_ADMIN)
        }
    }

    @Test
    fun profileEditAndReasonRulesMatchServerBoundary() {
        val edit = MemberManagementValidation.profileEdit("Valid Name", "+8801000000000", "M1", today.toString())
        assertEquals("Valid Name", edit.fullName)
        assertEquals("A valid reason", MemberManagementValidation.reason("  A valid reason  "))
        assertError(MemberManagementValidationError.REASON) {
            MemberManagementValidation.reason("short", minimumLength = 8)
        }
    }

    private fun assertError(expected: MemberManagementValidationError, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${expected.name}", error is MemberManagementValidationException)
        assertEquals(expected, (error as MemberManagementValidationException).error)
    }
}
