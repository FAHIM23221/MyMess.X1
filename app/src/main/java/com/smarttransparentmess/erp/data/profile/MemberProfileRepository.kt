package com.smarttransparentmess.erp.data.profile

import com.smarttransparentmess.erp.domain.model.MemberProfile

interface MemberProfileRepository {
    suspend fun loadMyProfile(): MemberProfile

    /** Accepts only a server-held invitation matching the confirmed Auth email, if needed. */
    suspend fun loadMyProfileOrAcceptInvitation(): MemberProfile = loadMyProfile()

    suspend fun updateMyPermittedFields(fullName: String, phoneNumber: String?): MemberProfile
}
