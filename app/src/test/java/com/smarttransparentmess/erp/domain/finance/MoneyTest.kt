package com.smarttransparentmess.erp.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesExactMinorUnitsWithoutFloatingPoint() {
        assertEquals(0L, Money.parseToMinor("0"))
        assertEquals(1L, Money.parseToMinor("0.01"))
        assertEquals(1230L, Money.parseToMinor(" 12.3 "))
        assertEquals(1234L, Money.parseToMinor("12.34"))
        assertEquals(999999999999999L, Money.parseToMinor("9999999999999.99"))
    }

    @Test
    fun rejectsAmbiguousNegativeOrExcessPrecisionAmounts() {
        listOf("", "-1", "+1", "1.234", "1e3", "one", "10000000000000").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Money.parseToMinor(value) }
        }
    }

    @Test
    fun formatsTheFullLongRangeExactly() {
        assertEquals("0.00", Money.format(0L))
        assertEquals("12.30", Money.format(1230L))
        assertEquals("-0.01", Money.format(-1L))
        assertEquals("-92233720368547758.08", Money.format(Long.MIN_VALUE))
        assertEquals("92233720368547758.07", Money.format(Long.MAX_VALUE))
    }
}
