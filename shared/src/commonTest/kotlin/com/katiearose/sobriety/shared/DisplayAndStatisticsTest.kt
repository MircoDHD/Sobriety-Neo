package com.katiearose.sobriety.shared

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for DisplaySettings.kt, Statistics.kt and the time-saving migration. */
class DisplayAndStatisticsTest {

    private val tz = TimeZone.of("Europe/Berlin")
    private val day = 24L * 60 * 60 * 1000

    private fun millis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0) =
        LocalDateTime(y, mo, d, h, mi, s).toInstant(tz).toEpochMilliseconds()

    private fun addiction(vararg attempts: Pair<Long, Long>, status: Addiction.Status = Addiction.Status.Ongoing): Addiction {
        val history = LinkedHashMap<Long, Long>()
        attempts.forEach { history[it.first] = it.second }
        return Addiction("t", status, history, Addiction.Priority.MEDIUM,
            LinkedHashMap(), LocalTime(0, 0), LinkedHashMap(), LinkedHashSet())
    }

    // 1 year 2 months 0 weeks 3 days 4 hours 5 minutes 6 seconds
    private val sample = ElapsedTime.between(
        millis(2025, 1, 1), millis(2026, 3, 4, 4, 5, 6), tz
    )

    @Test
    fun everythingIsShownByDefault() {
        val units = sample.visibleUnits(DisplaySettings()).map { it.first }
        assertEquals(
            listOf(TimeUnitLevel.YEARS, TimeUnitLevel.MONTHS, TimeUnitLevel.DAYS,
                TimeUnitLevel.HOURS, TimeUnitLevel.MINUTES, TimeUnitLevel.SECONDS),
            units
        )
        assertTrue(DisplaySettings().showsEverything)
    }

    @Test
    fun onlyTheLargestUnit() {
        assertEquals(listOf(TimeUnitLevel.YEARS to 1), sample.visibleUnits(DisplaySettings(maxUnits = 1)))
    }

    @Test
    fun hoursAndMinutesWithoutSeconds() {
        val short = ElapsedTime.between(millis(2026, 10, 1), millis(2026, 10, 1, 5, 30, 59), tz)
        val settings = DisplaySettings(maxUnits = 2, smallestUnit = TimeUnitLevel.MINUTES)
        assertEquals(listOf(TimeUnitLevel.HOURS to 5, TimeUnitLevel.MINUTES to 30), short.visibleUnits(settings))
    }

    @Test
    fun zeroUnitsInsideTheWindowAreSkipped() {
        // years, months -> months is 2, weeks would be 0
        val settings = DisplaySettings(maxUnits = 3)
        assertEquals(listOf(TimeUnitLevel.YEARS to 1, TimeUnitLevel.MONTHS to 2), sample.visibleUnits(settings))
    }

    @Test
    fun nothingVisibleBelowTheSmallestUnit() {
        val short = ElapsedTime.between(millis(2026, 10, 1), millis(2026, 10, 20), tz)
        assertTrue(short.visibleUnits(DisplaySettings(smallestUnit = TimeUnitLevel.YEARS)).isEmpty())
    }

    @Test
    fun nextMilestoneIsTheClosestOneNotReached() {
        val start = millis(2026, 10, 1)
        val a = addiction(start to 0L)
        a.milestones.add(1 to DateTimeUnit.DAY)
        a.milestones.add(1 to DateTimeUnit.WEEK)
        a.milestones.add(1 to DateTimeUnit.MONTH)
        val next = a.nextMilestone(start + 3 * day)!!
        assertEquals(1 to DateTimeUnit.WEEK, next.first)
        assertEquals(millis(2026, 10, 8), next.second)
        assertNull(a.nextMilestone(millis(2026, 12, 1)))
    }

    @Test
    fun statisticsOfSeveralAttempts() {
        val a = addiction(
            millis(2026, 9, 1) to millis(2026, 9, 11),   // 10 days
            millis(2026, 9, 11) to millis(2026, 9, 15),  // 4 days
            millis(2026, 9, 15) to 0L                    // running
        )
        val now = millis(2026, 10, 12)
        val st = a.statistics(now)
        assertEquals(3, st.attemptDurations.size)
        assertEquals(2, st.relapseCount)
        assertEquals(27 * day, st.currentStreak)
        assertEquals(27 * day, st.longestStreak)
        assertEquals(7 * day, st.averageFinished)
        assertEquals(41 * day, st.totalSober)
        assertEquals(1, st.relapsesLast30Days) // Sep 15 is within 30 days of Oct 12, Sep 11 is not
    }

    @Test
    fun relapsesAreCountedPerMonth() {
        val a = addiction(
            millis(2026, 8, 1) to millis(2026, 8, 20),
            millis(2026, 8, 20) to millis(2026, 10, 2),
            millis(2026, 10, 2) to 0L
        )
        val result = relapsesPerMonth(listOf(a), millis(2026, 10, 15), 3, tz)
        assertEquals(
            listOf(YearMonth(2026, 8) to 1, YearMonth(2026, 9) to 0, YearMonth(2026, 10) to 1),
            result
        )
    }

    @Test
    fun legacyTimeSavingBecomesATimeSaving() {
        val a = addiction(millis(2026, 10, 1) to 0L)
        a.timeSaving = LocalTime(1, 30)
        assertTrue(a.migrateLegacyTimeSaving("Time"))
        assertEquals(LocalTime(0, 0), a.timeSaving)
        assertEquals(Pair(1.5, TIME_SAVING_UNIT), a.savings["Time"])
        assertEquals(SavingCategory.TIME, a.savingsMeta["Time"]!!.category)
        assertFalse(a.migrateLegacyTimeSaving("Time"))
        // The calculation gives the same result as before: 90 minutes per day.
        val s = a.calculateSavings(millis(2026, 10, 3), tz)
        assertEquals(180.0, s.timeTotalMinutes!!, 1e-6)
    }

    @Test
    fun displaySettingsSurviveJson() {
        val a = addiction(millis(2026, 10, 1) to 0L)
        a.display = DisplaySettings(2, TimeUnitLevel.MINUTES, CounterMode.BOTH, setOf(CardButtons.DELETE))
        val json = Json { ignoreUnknownKeys = true }
        val ser = ListSerializer(AddictionSerializer)
        val back = json.decodeFromString(ser, json.encodeToString(ser, listOf(a))).single()
        assertEquals(a.display, back.display)
    }
}
