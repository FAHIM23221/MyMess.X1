package com.smarttransparentmess.erp.data.profile

import com.smarttransparentmess.erp.domain.model.MemberProfile

interface MemberProfileRepository {
    suspend fun loadMyProfile(): MemberProfile
    suspend fun updateMyPermittedFields(fullName: String, phoneNumber: String?): MemberProfile
}
