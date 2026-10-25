package com.smarttransparentmess.erp.domain.model

data class MemberProfile(
    val userId: String,
    val messId: String,
    val memberId: String?,
    val fullName: String,
    val phoneNumber: String?,
    val roomNumber: String?,
    val seatNumber: String?,
    val joinDate: String?,
    val role: AppRole,
    val accountStatus: AccountStatus,
    val isCurrentManager: Boolean,
    val avatarPath: String?,
    val createdAt: String?,
    val updatedAt: String?,
)
