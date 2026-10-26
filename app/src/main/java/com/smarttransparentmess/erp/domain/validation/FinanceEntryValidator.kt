package com.smarttransparentmess.erp.domain.validation

import com.smarttransparentmess.erp.domain.finance.AdjustmentEntry
import com.smarttransparentmess.erp.domain.finance.AdjustmentKind
import com.smarttransparentmess.erp.domain.finance.ChargeCategory
import com.smarttransparentmess.erp.domain.finance.ChargeEntry
import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.finance.PaymentEntry
import com.smarttransparentmess.erp.domain.finance.PaymentMethod
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

private const val MAX_AMOUNT_MINOR = 1_000_000_000_000L
private val messZone: ZoneId = ZoneId.of("Asia/Dhaka")

enum class FinanceValidationError {
    REQUIRED_SELECTION,
    AMOUNT,
    DATE,
    DATE_RANGE,
    DESCRIPTION,
    REASON,
    REFERENCE,
    NOTE,
    FUTURE_PAYMENT,
}

class FinanceValidationException(val error: FinanceValidationError) : IllegalArgumentException()

data class BillingPeriodEntry(
    val label: String,
    val startsOn: String,
    val endsOn: String,
)

object FinanceEntryValidator {
    fun amount(rawValue: String): Long {
        val value = try {
            Money.parseToMinor(rawValue)
        } catch (_: IllegalArgumentException) {
            throw FinanceValidationException(FinanceValidationError.AMOUNT)
        } catch (_: ArithmeticException) {
            throw FinanceValidationException(FinanceValidationError.AMOUNT)
        }
        if (value <= 0L || value > MAX_AMOUNT_MINOR) {
            throw FinanceValidationException(FinanceValidationError.AMOUNT)
        }
        return value
    }

    fun period(label: String, startsOn: String, endsOn: String): BillingPeriodEntry {
        val cleanLabel = text(label, 2, 80, FinanceValidationError.DESCRIPTION)
        val start = date(startsOn)!!
        val end = date(endsOn)!!
        if (LocalDate.parse(end) < LocalDate.parse(start)) {
            throw FinanceValidationException(FinanceValidationError.DATE_RANGE)
        }
        return BillingPeriodEntry(cleanLabel, start, end)
    }

    fun payment(
        memberId: String,
        billingPeriodId: String,
        amount: String,
        method: PaymentMethod,
        paidOn: String,
        note: String,
        reference: String,
        now: ZonedDateTime = ZonedDateTime.now(messZone),
    ): PaymentEntry {
        requireSelection(memberId, billingPeriodId)
        val date = date(paidOn)!!
        val paidAt = LocalDate.parse(date).atStartOfDay(messZone).toInstant()
        if (paidAt > now.plusMinutes(5).toInstant()) {
            throw FinanceValidationException(FinanceValidationError.FUTURE_PAYMENT)
        }
        val cleanNote = optionalText(note, 500, FinanceValidationError.NOTE)
        val cleanReference = reference.trim().takeIf { it.isNotEmpty() }?.let {
            text(it, 2, 120, FinanceValidationError.REFERENCE)
        }
        return PaymentEntry(
            memberId = memberId,
            billingPeriodId = billingPeriodId,
            amountMinor = amount(amount),
            method = method,
            paidAt = paidAt.toString(),
            note = cleanNote,
            transactionReference = cleanReference,
        )
    }

    fun charge(
        memberId: String,
        billingPeriodId: String,
        category: ChargeCategory,
        amount: String,
        description: String,
        dueDate: String,
    ): ChargeEntry {
        requireSelection(memberId, billingPeriodId)
        return ChargeEntry(
            memberId = memberId,
            billingPeriodId = billingPeriodId,
            category = category,
            amountMinor = amount(amount),
            description = text(description, 2, 240, FinanceValidationError.DESCRIPTION),
            dueDate = dueDate.trim().takeIf { it.isNotEmpty() }?.let { date(it) },
        )
    }

    fun adjustment(
        memberId: String,
        billingPeriodId: String,
        kind: AdjustmentKind,
        amount: String,
        reason: String,
    ): AdjustmentEntry {
        requireSelection(memberId, billingPeriodId)
        return AdjustmentEntry(
            memberId = memberId,
            billingPeriodId = billingPeriodId,
            kind = kind,
            amountMinor = amount(amount),
            reason = text(reason, 8, 500, FinanceValidationError.REASON),
        )
    }

    fun reversalReason(reason: String): String = text(reason, 8, 500, FinanceValidationError.REASON)

    private fun requireSelection(memberId: String, billingPeriodId: String) {
        if (memberId.isBlank() || billingPeriodId.isBlank()) {
            throw FinanceValidationException(FinanceValidationError.REQUIRED_SELECTION)
        }
    }

    private fun date(value: String): String? = try {
        LocalDate.parse(value.trim()).toString()
    } catch (_: DateTimeParseException) {
        throw FinanceValidationException(FinanceValidationError.DATE)
    }

    private fun optionalText(value: String, maxLength: Int, error: FinanceValidationError): String? {
        val clean = value.trim().takeIf { it.isNotEmpty() } ?: return null
        if (clean.length > maxLength) throw FinanceValidationException(error)
        return clean
    }

    private fun text(value: String, minLength: Int, maxLength: Int, error: FinanceValidationError): String {
        val clean = value.trim()
        if (clean.length !in minLength..maxLength) throw FinanceValidationException(error)
        return clean
    }
}
