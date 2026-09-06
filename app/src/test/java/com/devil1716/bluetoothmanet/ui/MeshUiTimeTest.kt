package com.devil1716.bluetoothmanet.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MeshUiTimeTest {
    @Test
    fun recentTimestampIsJustNow() {
        val now = 1_720_000_000_000L
        assertEquals("Just Now", formatInboxTime(now - 15_000L, now))
    }

    @Test
    fun yesterdayUsesLabel() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 6, 16, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yesterday = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        assertEquals("Yesterday", formatInboxTime(yesterday.timeInMillis, nowCal.timeInMillis))
    }
}
