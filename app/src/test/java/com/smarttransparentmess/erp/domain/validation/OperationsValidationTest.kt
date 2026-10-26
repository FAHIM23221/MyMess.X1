package com.smarttransparentmess.erp.domain.validation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationsValidationTest {
    @Test
    fun mealQuantitiesUseExactHalfMealUnits() {
        assertEquals(1, OperationsValidation.mealQuantityUnits("0.5"))
        assertEquals(3, OperationsValidation.mealQuantityUnits("1.5"))
        assertEquals(20, OperationsValidation.mealQuantityUnits("10"))

        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.mealQuantityUnits("1.25")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.mealQuantityUnits("10.5")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.mealQuantityUnits("0")
        }
    }

    @Test
    fun riceQuantitiesAreWholeBoundedGrams() {
        assertEquals(1, OperationsValidation.riceQuantityGrams("1"))
        assertEquals(1_000_000_000, OperationsValidation.riceQuantityGrams("1000000000"))

        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.riceQuantityGrams("1.5")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.riceQuantityGrams("0")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.riceQuantityGrams("1000000001")
        }
    }

    @Test
    fun expenseMoneyAndOptionalReferenceAreExact() {
        assertEquals(1_234L, OperationsValidation.expenseAmountMinor("12.34"))
        assertNull(OperationsValidation.transactionReference("  "))
        assertEquals("TX-1", OperationsValidation.transactionReference(" TX-1 "))

        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.expenseAmountMinor("0")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.expenseAmountMinor("1.234")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.transactionReference("X")
        }
    }

    @Test
    fun datesDescriptionsAndReversalReasonsAreValidated() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals(today, OperationsValidation.pastOrToday("2026-10-25", today))
        assertEquals("Groceries", OperationsValidation.expenseDescription(" Groceries "))
        assertEquals("A sufficient correction reason", OperationsValidation.reversalReason(" A sufficient correction reason "))

        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.pastOrToday("2026-10-26", today)
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.expenseDescription("X")
        }
        assertThrows(OperationsValidationException::class.java) {
            OperationsValidation.reversalReason("short")
        }
    }
}
