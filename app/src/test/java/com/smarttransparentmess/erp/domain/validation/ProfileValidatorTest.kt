package com.smarttransparentmess.erp.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    @Test
    fun nameMustContainTwoToEightyTrimmedCharacters() {
        assertFalse(ProfileValidator.validateName("  "))
        assertFalse(ProfileValidator.validateName("A"))
        assertTrue(ProfileValidator.validateName("  A B  "))
        assertTrue(ProfileValidator.validateName("x".repeat(80)))
        assertFalse(ProfileValidator.validateName("x".repeat(81)))
    }

    @Test
    fun phoneIsOptionalButValidatesCommonInternationalFormats() {
        assertTrue(ProfileValidator.validatePhone(""))
        assertTrue(ProfileValidator.validatePhone(" +880 (1712) 345678 "))
        assertTrue(ProfileValidator.validatePhone("০১৭১২৩৪৫৬৭৮"))
        assertFalse(ProfileValidator.validatePhone("123"))
        assertFalse(ProfileValidator.validatePhone("abc1234567"))
        assertFalse(ProfileValidator.validatePhone("1234567@"))
    }
}
