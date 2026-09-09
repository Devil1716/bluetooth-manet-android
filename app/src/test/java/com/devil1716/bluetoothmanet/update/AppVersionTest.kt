package com.devil1716.bluetoothmanet.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun normalizesTagPrefix() {
        assertEquals("1.3.3", AppVersion.normalize("v1.3.3"))
    }

    @Test
    fun detectsNewerRelease() {
        assertTrue(AppVersion.isNewer("1.3.3", "1.3.2"))
        assertFalse(AppVersion.isNewer("1.3.2", "1.3.2"))
        assertFalse(AppVersion.isNewer("1.3.1", "1.3.3"))
    }

    @Test
    fun comparesUnevenSegments() {
        assertTrue(AppVersion.isNewer("1.4", "1.3.9"))
        assertEquals(0, AppVersion.compare("v1.3.0", "1.3"))
    }
}
