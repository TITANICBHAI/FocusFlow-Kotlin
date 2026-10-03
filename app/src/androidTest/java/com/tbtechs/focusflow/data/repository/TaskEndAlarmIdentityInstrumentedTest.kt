package com.tbtechs.focusflow.data.repository

import android.app.PendingIntent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskEndAlarmIdentityInstrumentedTest {

    @Test
    fun twoTasksHaveIndependentAlarmAndActivityPendingIntents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val firstId = "alarm-test-${UUID.randomUUID()}-one"
        val secondId = "alarm-test-${UUID.randomUUID()}-two"
        val endMs = System.currentTimeMillis() + 60_000L

        val firstAlarm = AlarmRepository.buildAlarmPendingIntent(
            context, firstId, "first", endMs, flags,
        )
        val secondAlarm = AlarmRepository.buildAlarmPendingIntent(
            context, secondId, "second", endMs, flags,
        )
        val firstShow = AlarmRepository.buildShowPendingIntent(
            context, firstId, "first", endMs, flags,
        )
        val secondShow = AlarmRepository.buildShowPendingIntent(
            context, secondId, "second", endMs, flags,
        )

        try {
            assertNotEquals(firstAlarm, secondAlarm)
            assertNotEquals(firstShow, secondShow)
            assertNotEquals(
                TaskEndAlarmIdentity.notificationTag(firstId),
                TaskEndAlarmIdentity.notificationTag(secondId),
            )

            val sameIdentityUpdated = AlarmRepository.buildAlarmPendingIntent(
                context, firstId, "renamed", endMs + 30_000L, flags,
            )
            assertEquals(firstAlarm, sameIdentityUpdated)
            sameIdentityUpdated.cancel()
        } finally {
            firstAlarm.cancel()
            secondAlarm.cancel()
            firstShow.cancel()
            secondShow.cancel()
        }
    }

    @Test
    fun notificationDedupeIsPerTaskAndPerEndTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = TaskAlarmRegistry(context)
        val firstId = "dedupe-test-${UUID.randomUUID()}-one"
        val secondId = "dedupe-test-${UUID.randomUUID()}-two"
        val nowMs = System.currentTimeMillis()
        val firstEnd = nowMs + 5_000L
        val posts = AtomicInteger()

        assertTrue(registry.postOnce(firstId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertFalse(registry.postOnce(firstId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertTrue(registry.postOnce(secondId, firstEnd, nowMs) { posts.incrementAndGet() })
        assertTrue(registry.postOnce(firstId, firstEnd + 60_000L, nowMs) { posts.incrementAndGet() })
        assertEquals(3, posts.get())
    }
}