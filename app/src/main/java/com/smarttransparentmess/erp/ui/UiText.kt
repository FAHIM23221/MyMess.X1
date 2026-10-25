package com.smarttransparentmess.erp.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.presentation.AuthNotice
import com.smarttransparentmess.erp.presentation.ProfileUnavailableReason

@StringRes
fun AppRole.labelRes(): Int = when (this) {
    AppRole.MEMBER -> R.string.member_role
    AppRole.ADMIN -> R.string.admin_role
    AppRole.MAIN_ADMIN -> R.string.main_admin_role
}

@StringRes
fun AccountStatus.labelRes(): Int = when (this) {
    AccountStatus.ACTIVE -> R.string.active_status
    AccountStatus.PENDING -> R.string.pending_status
    AccountStatus.SUSPENDED -> R.string.suspended_status
}

@Composable
fun AuthNotice.textOrNull(): String? = when (this) {
    AuthNotice.SIGN_IN_FAILED -> stringResource(R.string.generic_signin_error)
    AuthNotice.ADMIN_ACCESS_REQUIRED -> stringResource(R.string.admin_not_member_message)
    AuthNotice.ACCOUNT_NOT_ACTIVE -> stringResource(R.string.account_not_active)
    AuthNotice.CREDENTIALS_REQUIRED -> stringResource(R.string.error_generic)
    AuthNotice.RECOVERY_REQUESTED -> stringResource(R.string.recovery_requested)
    AuthNotice.RECOVERY_FAILED -> stringResource(R.string.recovery_error)
    AuthNotice.PASSWORD_MISMATCH -> stringResource(R.string.password_mismatch)
    AuthNotice.PASSWORD_TOO_SHORT -> stringResource(R.string.password_too_short)
    AuthNotice.PASSWORD_UPDATED -> stringResource(R.string.password_updated)
    AuthNotice.PASSWORD_UPDATE_FAILED -> stringResource(R.string.password_update_error)
    AuthNotice.PROFILE_SAVED -> stringResource(R.string.profile_saved)
    AuthNotice.PROFILE_NAME_INVALID -> stringResource(R.string.name_invalid)
    AuthNotice.PROFILE_PHONE_INVALID -> stringResource(R.string.phone_invalid)
    AuthNotice.PROFILE_SAVE_FAILED -> stringResource(R.string.profile_save_error)
    AuthNotice.SESSION_EXPIRED -> stringResource(R.string.session_expired)
    AuthNotice.SIGNED_OUT_LOCAL_ONLY -> stringResource(R.string.signed_out_local_only)
}

@StringRes
fun ProfileUnavailableReason.titleRes(): Int = when (this) {
    ProfileUnavailableReason.INACTIVE_ACCOUNT -> R.string.profile_unavailable_title
    ProfileUnavailableReason.PROFILE_LOAD_FAILED -> R.string.profile_unavailable_title
}

@StringRes
fun ProfileUnavailableReason.bodyRes(): Int = when (this) {
    ProfileUnavailableReason.INACTIVE_ACCOUNT -> R.string.account_not_active
    ProfileUnavailableReason.PROFILE_LOAD_FAILED -> R.string.profile_unavailable_body
}
