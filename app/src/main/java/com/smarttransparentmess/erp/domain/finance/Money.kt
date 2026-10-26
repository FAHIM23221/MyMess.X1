package com.smarttransparentmess.erp.domain.finance

import java.math.BigDecimal

/** Bangladeshi taka is persisted as integer poisha; floating point is never used for ledger maths. */
object Money {
    const val CURRENCY_CODE = "BDT"
    private const val MINOR_UNITS_PER_MAJOR = 100L
    private val inputPattern = Regex("\\d{1,13}(?:\\.\\d{1,2})?")

    fun parseToMinor(rawValue: String): Long {
        val value = rawValue.trim()
        require(value.isNotEmpty()) { "An amount is required." }
        require(inputPattern.matches(value)) { "Use a positive amount with at most two decimal places." }

        val parts = value.split('.')
        val major = parts[0].toLong()
        val minor = parts.getOrNull(1).orEmpty().padEnd(2, '0').ifEmpty { "0" }.toLong()
        return Math.addExact(Math.multiplyExact(major, MINOR_UNITS_PER_MAJOR), minor)
    }

    fun format(minorUnits: Long): String = BigDecimal.valueOf(minorUnits, 2).toPlainString()

    fun sum(values: Iterable<Long>): Long = values.fold(0L) { total, value ->
        Math.addExact(total, value)
    }
}
