package com.katiearose.sobriety.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the behavior of [Addiction] that does not depend on the platform.
 *
 * These tests use the real clock ("now" plus or minus some days) and therefore only check
 * ranges and states, never exact timestamps.
 */
class AddictionTest {

    private val dayMillis = 24L * 60 * 60 * 1000
    private fun now() = Clock.System.now().toEpochMilliseconds()

    @Test
    fun newAddictionInThePastIsOngoing() {
        val a = Addiction.newInstance("test", now() - dayMillis, Addiction.Priority.LOW)
        assertEquals(Addiction.Status.Ongoing, a.status)
    }

    @Test
    fun futureAddictionBecomesOngoingWhenDue() {
        val start = now() + 60_000
        val a = Addiction.newInstance("test", start, Addiction.Priority.MEDIUM)
        assertEquals(Addiction.Status.Future, a.status)

        // Not due yet: nothing changes and the caller is told that nothing has to be saved.
        assertFalse(a.activateIfDue(start - 1))
        assertEquals(Addiction.Status.Future, a.status)

        // Exactly at the start time it switches, and only once.
        assertTrue(a.activateIfDue(start))
        assertEquals(Addiction.Status.Ongoing, a.status)
        assertFalse(a.activateIfDue(start + 1000))
    }

    @Test
    fun relapseStartsANewAttemptAndKeepsOldOnes() {
        val a = Addiction.newInstance("test", now() - 2 * dayMillis, Addiction.Priority.HIGH)
        a.relapse()
        assertEquals(2, a.history.size)
        // The first attempt is closed (end time set), the second one is running (end = 0).
        assertTrue(a.history.values.first() > 0)
        assertEquals(0L, a.history.values.last())
        assertEquals(Addiction.Status.Ongoing, a.status)
    }

    @Test
    fun averageOfNoAttemptsIsZeroInsteadOfCrashing() {
        val a = Addiction.newInstance("test", now() - dayMillis, Addiction.Priority.LOW)
        assertEquals(0L, a.calculateAvgRelapseDuration(emptyList()))
    }

    @Test
    fun monthMilestoneUsesCalendarMonths() {
        val start = now() - dayMillis
        val a = Addiction.newInstance("test", start, Addiction.Priority.LOW)
        val goal = a.milestoneGoalMillis(Pair(1, DateTimeUnit.MONTH))
        val lengthInDays = (goal - a.history.keys.last()) / dayMillis.toDouble()
        // A calendar month is 28 to 31 days (give or take an hour for daylight saving time).
        assertTrue(lengthInDays in 27.9..31.1, "was $lengthInDays days")
    }

    @Test
    fun milestoneProgressIsClampedToHundredPercent() {
        // Started 2 days ago, so a 1-day milestone is long reached.
        val a = Addiction.newInstance("test", now() - 2 * dayMillis, Addiction.Priority.LOW)
        assertEquals(100, a.calculateMilestoneProgressionPercentage(Pair(1, DateTimeUnit.DAY)).second)
        // A 1-year milestone has barely started.
        val yearProgress = a.calculateMilestoneProgressionPercentage(Pair(1, DateTimeUnit.YEAR)).second
        assertTrue(yearProgress in 0..2, "was $yearProgress")
    }

    @Test
    fun absurdlyLargeMilestoneDoesNotCrash() {
        val a = Addiction.newInstance("test", now() - dayMillis, Addiction.Priority.LOW)
        val progress = a.calculateMilestoneProgressionPercentage(Pair(Int.MAX_VALUE, DateTimeUnit.YEAR))
        assertEquals(0, progress.second)
    }

    @Test
    fun stoppedAddictionKeepsProgressAtStopTime() {
        // Started 10 days ago, stopped now: a 5-day milestone was reached, a 1-year one was not.
        val a = Addiction.newInstance("test", now() - 10 * dayMillis, Addiction.Priority.LOW)
        a.stopAbstaining()
        assertEquals(Addiction.Status.Stopped, a.status)
        assertEquals(100, a.calculateMilestoneProgressionPercentage(Pair(5, DateTimeUnit.DAY)).second)
        assertTrue(a.calculateMilestoneProgressionPercentage(Pair(1, DateTimeUnit.YEAR)).second < 5)
    }
}
