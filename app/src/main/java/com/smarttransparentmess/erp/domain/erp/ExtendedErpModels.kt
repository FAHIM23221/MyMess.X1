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
    val loadedAt: String,
)

enum class ErpAction(val wireValue: String) {
    CREATE_FINE("create_fine"),
    REVIEW_PAYMENT("review_payment"),
    SET_MEAL_BILLING_POLICY("set_meal_billing_policy"),
    GENERATE_MEAL_BILL("generate_meal_bill"),
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
        }
        return values
    }
}
