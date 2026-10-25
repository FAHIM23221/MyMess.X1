package com.smarttransparentmess.erp.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationsCalculatorTest {
    @Test
    fun reversedRowsStayInHistoryButAreExcludedFromActiveTotals() {
        val snapshot = OperationsSnapshot(
            meals = listOf(meal("meal-1", 3), meal("meal-2", 2)),
            mealReversals = listOf(MealReversal("meal-2", "Incorrect meal entry", "now")),
            riceMovements = listOf(
                RiceMovement("rice-1", RiceMovementKind.STOCK_IN, 5_000, "Purchase", "now"),
                RiceMovement("rice-2", RiceMovementKind.STOCK_OUT, 2_000, "Issue", "now"),
            ),
            expenses = listOf(expense("expense-1", 1_000), expense("expense-2", 500)),
            expenseReversals = listOf(ExpenseReversal("expense-2", "Incorrect expense", "now")),
            cashBook = listOf(
                cash("cash-1", CashBookDirection.INFLOW, 2_000),
                cash("cash-2", CashBookDirection.INFLOW, 500),
                cash("cash-3", CashBookDirection.OUTFLOW, 2_000),
                cash("cash-4", CashBookDirection.OUTFLOW, 1_000),
            ),
        )

        val summary = OperationsCalculator.summarize(snapshot)

        assertEquals(2, snapshot.meals.size)
        assertEquals(2, snapshot.expenses.size)
        assertEquals(3L, summary.activeMealUnits)
        assertEquals(3_000L, summary.riceStockGrams)
        assertEquals(1_000L, summary.activeExpensesMinor)
        assertEquals(2_500L, summary.cashInflowMinor)
        assertEquals(3_000L, summary.cashOutflowMinor)
        assertEquals(-500L, summary.cashBalanceMinor)
    }

    @Test
    fun halfMealFormattingDoesNotUseFloatingPoint() {
        assertEquals("0", formatMealUnits(0))
        assertEquals("0.5", formatMealUnits(1))
        assertEquals("1", formatMealUnits(2))
        assertEquals("1.5", formatMealUnits(3))
        assertEquals("10", formatMealUnits(20))
    }

    @Test
    fun cashTotalOverflowIsNotSilentlyWrapped() {
        val snapshot = OperationsSnapshot(
            cashBook = listOf(
                cash("cash-1", CashBookDirection.INFLOW, Long.MAX_VALUE),
                cash("cash-2", CashBookDirection.INFLOW, 1),
            ),
        )

        assertThrows(ArithmeticException::class.java) {
            OperationsCalculator.summarize(snapshot)
        }
    }

    private fun meal(id: String, units: Int) = MealEntry(
        id = id,
        memberId = "member-1",
        memberName = "Member One",
        mealDate = "2026-10-25",
        slot = MealSlot.LUNCH,
        quantityUnits = units,
        note = null,
        createdAt = "now",
    )

    private fun expense(id: String, amount: Long) = ExpenseRecord(
        id = id,
        category = ExpenseCategory.GROCERY,
        amountMinor = amount,
        spentOn = "2026-10-25",
        description = "Groceries",
        transactionReference = null,
        createdAt = "now",
    )

    private fun cash(id: String, direction: CashBookDirection, amount: Long) = CashBookEntry(
        id = id,
        sourceType = "PAYMENT",
        sourceId = "source-$id",
        occurredAt = "now",
        direction = direction,
        amountMinor = amount,
        description = "Cash event",
        memberId = null,
        memberName = null,
    )
}
