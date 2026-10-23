package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.isNewerVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(isNewerVersion("1.5.8", "1.5.7"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.5.8", "1.5.8"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.5.6", "1.5.7"))
    }

    @Test
    fun twoDigitPartsCompareNumerically() {
        assertTrue(isNewerVersion("1.5.10", "1.5.9"))
        assertFalse(isNewerVersion("1.5.9", "1.5.10"))
    }

    @Test
    fun vPrefixAndSuffixAreIgnored() {
        assertTrue(isNewerVersion("v1.6.0", "1.5.9"))
        assertTrue(isNewerVersion("1.6.0-rc1", "1.5.9"))
        assertFalse(isNewerVersion("v1.5.7", "1.5.7"))
    }

    @Test
    fun missingPartsTreatedAsZero() {
        assertTrue(isNewerVersion("1.6", "1.5.9"))
        assertFalse(isNewerVersion("1.5", "1.5.0"))
    }
}
