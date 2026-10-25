package com.smarttransparentmess.erp.domain.erp

/** A deliberately read-only record projection returned by the authorized snapshot RPC. */
data class ErpRecord(
    val id: String,
    val values: Map<String, String>,
) {
    fun text(key: String): String = values[key].orEmpty()
    fun long(key: String): Long? = values[key]?.toLongOrNull()
    fun boolean(key: String): Boolean = values[key].equals("true", ignoreCase = true)
    val status: String get() = text("status")
}

data class ExtendedErpSnapshot(
    val members: List<ErpRecord> = emptyList(),
    val periods: List<ErpRecord> = emptyList(),
    val fines: List<ErpRecord> = emptyList(),
    val paymentReviews: List<ErpRecord> = emptyList(),
    val mealBillingPolicies: List<ErpRecord> = emptyList(),
    val mealBillingRuns: List<ErpRecord> = emptyList(),
    val mealSchedules: List<ErpRecord> = emptyList(),
    val ricePurchases: List<ErpRecord> = emptyList(),
    val riceMovements: List<ErpRecord> = emptyList(),
    val riceMovementReversals: List<ErpRecord> = emptyList(),
    val expenses: List<ErpRecord> = emptyList(),
    val messSettings: List<ErpRecord> = emptyList(),
    val notifications: List<ErpRecord> = emptyList(),
    val riceStockAlerts: List<ErpRecord> = emptyList(),
    val cashAccounts: List<ErpRecord> = emptyList(),
    val cashEntries: List<ErpRecord> = emptyList(),
    val cashPeriodLocks: List<ErpRecord> = emptyList(),
    val loadedAt: String,
)

enum class ErpAction(val wireValue: String) {
    CREATE_FINE("create_fine"),
    REVIEW_PAYMENT("review_payment"),
    SET_MEAL_BILLING_POLICY("set_meal_billing_policy"),
    GENERATE_MEAL_BILL("generate_meal_bill"),
    SET_MEAL_SCHEDULE("set_meal_schedule"),
    RECORD_RICE_PURCHASE("record_rice_purchase"),
    REVERSE_RICE_MOVEMENT("reverse_rice_movement"),
    UPDATE_EXPENSE_DETAILS("update_expense_details"),
    REVIEW_EXPENSE("review_expense"),
    UPSERT_CASH_ACCOUNT("upsert_cash_account"),
    ASSIGN_CASH_ENTRY_ACCOUNT("assign_cash_entry_account"),
    CLOSE_CASH_PERIOD("close_cash_period"),
    UPDATE_MESS_SETTINGS("update_mess_settings"),
    MARK_NOTIFICATION_READ("mark_notification_read"),
}

enum class ExtendedErpValidationError {
    REQUIRED,
    AMOUNT,
    DATE,
    REASON,
    SELECTION,
}

class ExtendedErpValidationException(
    val error: ExtendedErpValidationError,
) : IllegalArgumentException(error.name)

object ExtendedErpValidation {
    private val isoDate = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(action: ErpAction, input: Map<String, String>): Map<String, String> {
        val values = input.mapValues { it.value.trim() }
        fun required(vararg keys: String) {
            if (keys.any { values[it].isNullOrBlank() }) {
                throw ExtendedErpValidationException(ExtendedErpValidationError.REQUIRED)
            }
        }
        fun amount(key: String) {
            val value = values[key]?.toLongOrNull()
            if (value == null || value <= 0L || value > 1_000_000_000_000L) {
                throw ExtendedErpValidationException(ExtendedErpValidationError.AMOUNT)
            }
        }
        fun optionalDate(key: String) {
            val value = values[key].orEmpty()
            if (value.isNotEmpty() && (!isoDate.matches(value) || runCatching { java.time.LocalDate.parse(value) }.isFailure)) {
                throw ExtendedErpValidationException(ExtendedErpValidationError.DATE)
            }
        }
        when (action) {
            ErpAction.CREATE_FINE -> {
                required("member_id", "period_id", "amount_minor", "reason")
                amount("amount_minor")
                if (values.getValue("reason").length !in 8..500) {
                    throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
                }
                optionalDate("due_date")
            }
            ErpAction.REVIEW_PAYMENT -> {
                required("payment_id", "status")
                if (values.getValue("status") !in setOf("VERIFIED", "REJECTED")) {
                    throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                }
                if (values.getValue("status") == "REJECTED" && values["reason"].orEmpty().length !in 8..500) {
                    throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
                }
            }
            ErpAction.SET_MEAL_BILLING_POLICY -> {
                required("price_per_half_meal_minor", "enabled")
                amount("price_per_half_meal_minor")
                if (values.getValue("enabled") !in setOf("true", "false")) {
                    throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                }
            }
            ErpAction.GENERATE_MEAL_BILL -> {
                required("period_id")
                optionalDate("due_date")
            }
            ErpAction.SET_MEAL_SCHEDULE -> {
                required("member_id", "starts_on", "status")
                optionalDate("starts_on")
                optionalDate("ends_on")
                if (values.getValue("status") !in setOf("ON", "OFF")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                if (values["reason"].orEmpty().isNotEmpty() && values.getValue("reason").length !in 2..500) throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
            }
            ErpAction.RECORD_RICE_PURCHASE -> {
                required("quantity_grams", "unit", "unit_price_minor", "vendor", "purchased_on")
                amount("quantity_grams")
                amount("unit_price_minor")
                optionalDate("purchased_on")
                if (values.getValue("unit") !in setOf("GRAM", "KG")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                if (values.getValue("vendor").length !in 2..160) throw ExtendedErpValidationException(ExtendedErpValidationError.REQUIRED)
            }
            ErpAction.REVERSE_RICE_MOVEMENT -> {
                required("movement_id", "reason")
                if (values.getValue("reason").length !in 8..500) throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
            }
            ErpAction.UPDATE_EXPENSE_DETAILS -> {
                required("expense_id")
                if (values["vendor"].orEmpty().isNotEmpty() && values.getValue("vendor").length !in 2..160) throw ExtendedErpValidationException(ExtendedErpValidationError.REQUIRED)
            }
            ErpAction.REVIEW_EXPENSE -> {
                required("expense_id", "status")
                if (values.getValue("status") !in setOf("APPROVED", "REJECTED")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                if (values.getValue("status") == "REJECTED" && values["reason"].orEmpty().length !in 8..500) throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
            }
            ErpAction.UPSERT_CASH_ACCOUNT -> {
                required("name", "kind", "opening_balance_minor")
                if (values.getValue("kind") !in setOf("CASH", "BANK", "MOBILE_WALLET")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                if (values.getValue("opening_balance_minor").toLongOrNull() == null) throw ExtendedErpValidationException(ExtendedErpValidationError.AMOUNT)
            }
            ErpAction.ASSIGN_CASH_ENTRY_ACCOUNT -> required("cash_book_entry_id", "account_id")
            ErpAction.CLOSE_CASH_PERIOD -> {
                required("starts_on", "ends_on", "opening_balance_minor")
                optionalDate("starts_on")
                optionalDate("ends_on")
                if (values.getValue("opening_balance_minor").toLongOrNull() == null) throw ExtendedErpValidationException(ExtendedErpValidationError.AMOUNT)
                if (values["note"].orEmpty().isNotEmpty() && values.getValue("note").length !in 8..500) throw ExtendedErpValidationException(ExtendedErpValidationError.REASON)
            }
            ErpAction.UPDATE_MESS_SETTINGS -> {
                required("locale", "low_rice_threshold_grams", "meal_reminders_enabled")
                if (values.getValue("locale") !in setOf("bn", "en")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
                val threshold = values.getValue("low_rice_threshold_grams").toIntOrNull()
                if (threshold == null || threshold !in 0..1000000000) throw ExtendedErpValidationException(ExtendedErpValidationError.AMOUNT)
                if (values.getValue("meal_reminders_enabled") !in setOf("true", "false")) throw ExtendedErpValidationException(ExtendedErpValidationError.SELECTION)
            }
            ErpAction.MARK_NOTIFICATION_READ -> required("notification_id")
        }
        return values
    }
}
