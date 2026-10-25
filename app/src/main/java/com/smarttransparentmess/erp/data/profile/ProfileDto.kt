package com.smarttransparentmess.erp.data.profile

import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.model.MemberProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("mess_id") val messId: String,
    @SerialName("member_id") val memberId: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("room_number") val roomNumber: String? = null,
    @SerialName("seat_number") val seatNumber: String? = null,
    @SerialName("join_date") val joinDate: String? = null,
    val role: String,
    @SerialName("account_status") val accountStatus: String,
    @SerialName("is_current_manager") val isCurrentManager: Boolean = false,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain(): MemberProfile {
        val parsedRole = AppRole.fromDatabase(role)
            ?: error("The backend returned an unsupported role")
        val parsedStatus = AccountStatus.fromDatabase(accountStatus)
            ?: error("The backend returned an unsupported account status")
        return MemberProfile(
            userId = userId,
            messId = messId,
            memberId = memberId,
            fullName = fullName,
            phoneNumber = phoneNumber,
            roomNumber = roomNumber,
            seatNumber = seatNumber,
            joinDate = joinDate,
            role = parsedRole,
            accountStatus = parsedStatus,
            isCurrentManager = isCurrentManager,
            avatarPath = avatarPath,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
data class ProfileUpdateDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String?,
)
