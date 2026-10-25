package com.smarttransparentmess.erp.domain.operations

import com.smarttransparentmess.erp.domain.finance.Money
import com.smarttransparentmess.erp.domain.model.MemberProfile

enum class MealSlot { BREAKFAST, LUNCH, DINNER }

enum class RiceMovementKind { STOCK_IN, STOCK_OUT }

enum class ExpenseCategory { RICE, GROCERY, UTILITY, MAINTENANCE, OTHER }

enum class CashBookDirection { INFLOW, OUTFLOW }

data class MealEntry(
    val id: String,
    val memberId: String,
    val memberName: String,
    val mealDate: String,
    val slot: MealSlot,
    val quantityUnits: Int,
    val note: String?,
    val createdAt: String,
)

data class MealReversal(
    val mealEntryId: String,
    val reason: String,
    val createdAt: String,
)

data class RiceMovement(
    val id: String,
    val kind: RiceMovementKind,
    val quantityGrams: Int,
    val note: String,
    val createdAt: String,
)

data class ExpenseRecord(
    val id: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val spentOn: String,
    val description: String,
    val transactionReference: String?,
    val createdAt: String,
)

data class ExpenseReversal(
    val expenseId: String,
    val reason: String,
    val createdAt: String,
)

data class CashBookEntry(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val occurredAt: String,
    val direction: CashBookDirection,
    val amountMinor: Long,
    val description: String,
    val memberId: String?,
    val memberName: String?,
)

data class OperationsSnapshot(
    val members: List<MemberProfile> = emptyList(),
    val meals: List<MealEntry> = emptyList(),
    val mealReversals: List<MealReversal> = emptyList(),
    val riceMovements: List<RiceMovement> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val expenseReversals: List<ExpenseReversal> = emptyList(),
    val cashBook: List<CashBookEntry> = emptyList(),
)

data class OperationsSummary(
    val activeMealUnits: Long,
    val riceStockGrams: Long,
    val activeExpensesMinor: Long,
    val cashInflowMinor: Long,
    val cashOutflowMinor: Long,
) {
    val cashBalanceMinor: Long = Math.subtractExact(cashInflowMinor, cashOutflowMinor)
}

object OperationsCalculator {
    fun summarize(snapshot: OperationsSnapshot): OperationsSummary {
        val reversedMealIds = snapshot.mealReversals.mapTo(hashSetOf()) { it.mealEntryId }
        val reversedExpenseIds = snapshot.expenseReversals.mapTo(hashSetOf()) { it.expenseId }
        return OperationsSummary(
            activeMealUnits = snapshot.meals
                .filter { it.id !in reversedMealIds }
                .fold(0L) { total, meal -> Math.addExact(total, meal.quantityUnits.toLong()) },
            riceStockGrams = snapshot.riceMovements.fold(0L) { total, movement ->
                Math.addExact(
                    total,
                    if (movement.kind == RiceMovementKind.STOCK_IN) {
                        movement.quantityGrams.toLong()
                    } else {
                        -movement.quantityGrams.toLong()
                    },
                )
            },
            activeExpensesMinor = Money.sum(
                snapshot.expenses.filter { it.id !in reversedExpenseIds }.map { it.amountMinor },
            ),
            cashInflowMinor = Money.sum(
                snapshot.cashBook.filter { it.direction == CashBookDirection.INFLOW }.map { it.amountMinor },
            ),
            cashOutflowMinor = Money.sum(
                snapshot.cashBook.filter { it.direction == CashBookDirection.OUTFLOW }.map { it.amountMinor },
            ),
        )
    }
}

fun formatMealUnits(units: Long): String = if (units % 2L == 0L) {
    (units / 2L).toString()
} else {
    "${units / 2L}.5"
}
