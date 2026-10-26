package com.smarttransparentmess.erp.domain.erp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedErpOperationsValidationTest {
    @Test
    fun scheduleAndPurchaseInputsAreValidated() {
        val schedule = ExtendedErpValidation.validate(
            ErpAction.SET_MEAL_SCHEDULE,
            mapOf("member_id" to "m", "starts_on" to "2026-10-25", "ends_on" to "2026-10-31", "status" to "OFF", "reason" to "Away"),
        )
        assertEquals("OFF", schedule.getValue("status"))
        val purchase = ExtendedErpValidation.validate(
            ErpAction.RECORD_RICE_PURCHASE,
            mapOf("quantity_grams" to "5000", "unit" to "KG", "unit_price_minor" to "1000", "vendor" to "Vendor", "purchased_on" to "2026-10-25"),
        )
        assertEquals("5000", purchase.getValue("quantity_grams"))
    }

    @Test
    fun cashAndSettingsBoundariesAreChecked() {
        val error = runCatching {
            ExtendedErpValidation.validate(
                ErpAction.UPDATE_MESS_SETTINGS,
                mapOf("locale" to "bn", "low_rice_threshold_grams" to "-1", "meal_reminders_enabled" to "true"),
            )
        }.exceptionOrNull()
        assertTrue(error is ExtendedErpValidationException)
        assertEquals(ExtendedErpValidationError.AMOUNT, (error as ExtendedErpValidationException).error)
        val cash = ExtendedErpValidation.validate(
            ErpAction.UPSERT_CASH_ACCOUNT,
            mapOf("name" to "Cash", "kind" to "CASH", "opening_balance_minor" to "0"),
        )
        assertEquals("0", cash.getValue("opening_balance_minor"))
    }
}
