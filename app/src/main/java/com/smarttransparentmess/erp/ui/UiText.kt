package com.smarttransparentmess.erp.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.smarttransparentmess.erp.R
import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import com.smarttransparentmess.erp.domain.model.AccountStatus
import com.smarttransparentmess.erp.domain.model.AppRole
import com.smarttransparentmess.erp.domain.validation.FinanceValidationError
import com.smarttransparentmess.erp.presentation.AdminFinanceNotice
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

@StringRes
fun PaymentMethod.labelRes(): Int = when (this) {
    PaymentMethod.CASH -> R.string.payment_method_cash
    PaymentMethod.BKASH -> R.string.payment_method_bkash
    PaymentMethod.NAGAD -> R.string.payment_method_nagad
    PaymentMethod.BANK -> R.string.payment_method_bank
    PaymentMethod.MOBILE_BANKING -> R.string.payment_method_mobile_banking
    PaymentMethod.OTHER -> R.string.payment_method_other
}

@StringRes
fun ChargeCategory.labelRes(): Int = when (this) {
    ChargeCategory.SEAT_RENT -> R.string.charge_seat_rent
    ChargeCategory.MEAL_CHARGE -> R.string.charge_meal
    ChargeCategory.ELECTRICITY -> R.string.charge_electricity
    ChargeCategory.WIFI -> R.string.charge_wifi
    ChargeCategory.GAS -> R.string.charge_gas
    ChargeCategory.WATER -> R.string.charge_water
    ChargeCategory.FINE -> R.string.charge_fine
    ChargeCategory.UTILITY -> R.string.charge_utility
    ChargeCategory.SERVICE -> R.string.charge_service
    ChargeCategory.OTHER -> R.string.charge_other
}

@StringRes
fun AdjustmentKind.labelRes(): Int = when (this) {
    AdjustmentKind.OPENING_DUE -> R.string.adjustment_opening_due
    AdjustmentKind.CREDIT -> R.string.adjustment_credit
    AdjustmentKind.DEBIT_ADJUSTMENT -> R.string.adjustment_debit
}

@StringRes
fun FinanceValidationError.messageRes(): Int = when (this) {
    FinanceValidationError.REQUIRED_SELECTION -> R.string.validation_required_selection
    FinanceValidationError.AMOUNT -> R.string.validation_amount
    FinanceValidationError.DATE -> R.string.validation_date
    FinanceValidationError.DATE_RANGE -> R.string.validation_date_range
    FinanceValidationError.DESCRIPTION -> R.string.validation_description
    FinanceValidationError.REASON -> R.string.validation_reason
    FinanceValidationError.REFERENCE -> R.string.validation_reference
    FinanceValidationError.NOTE -> R.string.validation_note
    FinanceValidationError.FUTURE_PAYMENT -> R.string.validation_future_payment
}

@StringRes
fun AdminFinanceNotice.messageRes(): Int = when (this) {
    AdminFinanceNotice.PERIOD_CREATED -> R.string.period_created
    AdminFinanceNotice.PERIOD_CLOSED -> R.string.period_closed
    AdminFinanceNotice.CHARGE_CREATED -> R.string.charge_created
    AdminFinanceNotice.ADJUSTMENT_CREATED -> R.string.adjustment_created
    AdminFinanceNotice.PAYMENT_RECORDED -> R.string.payment_recorded
    AdminFinanceNotice.PAYMENT_REVERSED -> R.string.payment_reversed
    AdminFinanceNotice.CARRY_FORWARD_COMPLETED -> R.string.carry_forward_completed
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
