package com.smarttransparentmess.erp.domain.validation

import com.smarttransparentmess.erp.domain.members.MemberProfileEditInput
import com.smarttransparentmess.erp.domain.members.NewMemberInput
import com.smarttransparentmess.erp.domain.model.AppRole
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Locale

enum class MemberManagementValidationError {
    REQUIRED_FIELDS,
    NAME,
    EMAIL,
    PHONE,
    MEMBER_ID,
    JOIN_DATE,
    ROLE,
    REASON,
}

class MemberManagementValidationException(
    val error: MemberManagementValidationError,
) : IllegalArgumentException(error.name)

object MemberManagementValidation {
    private val phonePattern = Regex("^[0-9+() -]{7,20}$")
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun newMember(
        fullName: String,
        email: String,
        phoneNumber: String,
        memberId: String,
        joinDate: String,
        intendedRole: AppRole,
    ): NewMemberInput {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanPhone = phoneNumber.trim()
        val cleanMemberId = memberId.trim()
        val cleanDate = joinDate.trim()
        if (cleanName.length !in 2..80) fail(MemberManagementValidationError.NAME)
        if (cleanEmail.length !in 3..320 || !emailPattern.matches(cleanEmail)) {
            fail(MemberManagementValidationError.EMAIL)
        }
        if (!phonePattern.matches(cleanPhone)) fail(MemberManagementValidationError.PHONE)
        if (cleanMemberId.length !in 1..50) fail(MemberManagementValidationError.MEMBER_ID)
        validateJoinDate(cleanDate)
        if (intendedRole !in setOf(AppRole.MEMBER, AppRole.ADMIN)) {
            fail(MemberManagementValidationError.ROLE)
        }
        return NewMemberInput(
            fullName = cleanName,
            email = cleanEmail,
            phoneNumber = cleanPhone,
            memberId = cleanMemberId,
            joinDate = cleanDate,
            intendedRole = intendedRole,
        )
    }

    fun profileEdit(
        fullName: String,
        phoneNumber: String,
        memberId: String,
        joinDate: String,
    ): MemberProfileEditInput {
        val cleanName = fullName.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanMemberId = memberId.trim()
        val cleanDate = joinDate.trim()
        if (cleanName.length !in 2..80) fail(MemberManagementValidationError.NAME)
        if (!phonePattern.matches(cleanPhone)) fail(MemberManagementValidationError.PHONE)
        if (cleanMemberId.length !in 1..50) fail(MemberManagementValidationError.MEMBER_ID)
        validateJoinDate(cleanDate)
        return MemberProfileEditInput(cleanName, cleanPhone, cleanMemberId, cleanDate)
    }

    fun reason(value: String, minimumLength: Int = 3): String {
        val clean = value.trim()
        if (clean.length !in minimumLength..500) fail(MemberManagementValidationError.REASON)
        return clean
    }

    private fun validateJoinDate(value: String) {
        val date = try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            fail(MemberManagementValidationError.JOIN_DATE)
        }
        val today = LocalDate.now(ZoneId.of("Asia/Dhaka"))
        if (date < today.minusDays(3650) || date > today.plusDays(365)) {
            fail(MemberManagementValidationError.JOIN_DATE)
        }
    }

    private fun fail(error: MemberManagementValidationError): Nothing {
        throw MemberManagementValidationException(error)
    }
}
