package com.katiearose.sobriety.shared

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the savings calculation (SavingsCalculator.kt) and for storing the new Sobriety Neo
 * fields in JSON.
 *
 * All times are fixed (no real clock), in the Europe/Berlin time zone.
 */
class SavingsCalculatorTest {

    private val tz = TimeZone.of("Europe/Berlin")
    private val day = 24L * 60 * 60 * 1000

    private fun millis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        LocalDateTime(y, mo, d, h, mi).toInstant(tz).toEpochMilliseconds()

    private fun assertNear(expected: Double, actual: Double, eps: Double = 1e-6) =
        assertTrue(abs(expected - actual) < eps, "expected $expected but was $actual")

    /** An addiction with one running attempt that started at [start]. */
    private fun running(start: Long): Addiction {
        val history = LinkedHashMap<Long, Long>()
        history[start] = 0
        return Addiction("t", Addiction.Status.Ongoing, history, Addiction.Priority.MEDIUM,
            LinkedHashMap(), LocalTime(0, 0), LinkedHashMap(), LinkedHashSet())
    }

    private fun Addiction.addMoney(name: String, perDay: Double, currency: String = "€") {
        savings[name] = Pair(perDay, currency)
        savingsMeta[name] = SavingMeta(SavingCategory.MONEY, RatePeriod.DAY, perDay)
    }

    @Test
    fun moneyGrowsWithSoberDays() {
        val start = millis(2026, 10, 1)
        val now = start + 10 * day
        val a = running(start)
        a.addMoney("Cigarettes", 5.0)
        val s = a.calculateSavings(now, tz)
        assertNear(50.0, s.moneyByCurrency.getValue("€").total)
        // The whole attempt lies in October, so "this month" is the same.
        assertNear(50.0, s.moneyByCurrency.getValue("€").thisMonth)
    }

    @Test
    fun thisMonthOnlyCountsTheCurrentMonth() {
        val start = millis(2026, 9, 21)
        val now = millis(2026, 10, 6)
        val a = running(start)
        a.addMoney("Beer", 2.0)
        val s = a.calculateSavings(now, tz).moneyByCurrency.getValue("€")
        assertNear(2.0 * 15, s.total, 0.01)
        assertNear(2.0 * 5, s.thisMonth, 0.01)
    }

    @Test
    fun relapseCostsAreSubtractedAndTimeBetweenAttemptsDoesNotCount() {
        val a = running(millis(2026, 10, 1))
        // Attempt 1: Oct 1 - Oct 5 (4 days), attempt 2: Oct 5 - now (Oct 8, 3 days)
        a.history.clear()
        a.history[millis(2026, 10, 1)] = millis(2026, 10, 5)
        a.history[millis(2026, 10, 5)] = 0
        a.addMoney("Money", 10.0)
        a.timeSaving = LocalTime(1, 0) // one hour per day
        a.relapseCosts.add(RelapseCost(millis(2026, 10, 5), money = 15.0, minutes = 90))

        val s = a.calculateSavings(millis(2026, 10, 8), tz)
        assertNear(7 * 10.0 - 15.0, s.moneyByCurrency.getValue("€").total)
        assertNear(7 * 60.0 - 90.0, s.timeTotalMinutes!!)
        assertEquals(15.0, s.moneySpentTotal)
        assertEquals(90L, s.minutesSpentTotal)
    }

    @Test
    fun stoppedAttemptsStopSaving() {
        val a = running(millis(2026, 10, 1))
        a.history[millis(2026, 10, 1)] = millis(2026, 10, 3)
        a.status = Addiction.Status.Stopped
        a.addMoney("Money", 1.0)
        val s = a.calculateSavings(millis(2026, 10, 20), tz)
        assertNear(2.0, s.moneyByCurrency.getValue("€").total)
    }

    @Test
    fun futureAttemptsSaveNothing() {
        val now = millis(2026, 10, 1)
        val a = running(now + day)
        a.status = Addiction.Status.Future
        a.addMoney("Money", 100.0)
        assertNear(0.0, a.calculateSavings(now, tz).moneyByCurrency.getValue("€").total)
    }

    @Test
    fun customSavingsAndCurrenciesAreKeptApart() {
        val start = millis(2026, 10, 1)
        val a = running(start)
        a.addMoney("A", 1.0, "€")
        a.addMoney("B", 2.0, "€")
        a.addMoney("C", 3.0, "USD")
        a.savings["Cigarettes"] = Pair(20.0, "pcs") // no meta: legacy entry -> CUSTOM
        val s = a.calculateSavings(start + 2 * day, tz)
        assertNear(6.0, s.moneyByCurrency.getValue("€").total)
        assertNear(6.0, s.moneyByCurrency.getValue("USD").total)
        val cigarettes = s.items.single { it.name == "Cigarettes" }
        assertEquals(SavingCategory.CUSTOM, cigarettes.category)
        assertNear(40.0, cigarettes.total)
        assertEquals("€", a.primaryCurrency)
    }

    @Test
    fun noTimeSavingMeansNoTimeTotal() {
        val a = running(millis(2026, 10, 1))
        val s = a.calculateSavings(millis(2026, 10, 2), tz)
        assertNull(s.timeTotalMinutes)
        assertTrue(s.isEmpty)
    }

    @Test
    fun relapseStoresCostOnlyForRunningAttempts() {
        val a = running(millis(2026, 1, 1))
        a.relapse(RelapseCost(0, money = 5.0, minutes = 30))
        assertEquals(1, a.relapseCosts.size)
        // Empty costs are not stored.
        a.relapse(RelapseCost(0))
        assertEquals(1, a.relapseCosts.size)
        // Starting a stopped addiction again is not a relapse.
        a.stopAbstaining()
        a.relapse(RelapseCost(0, money = 99.0))
        assertEquals(1, a.relapseCosts.size)
    }

    @Test
    fun newFieldsSurviveJsonRoundTrip() {
        val a = running(millis(2026, 10, 1))
        a.addMoney("Money", 4.5)
        a.relapseCosts.add(RelapseCost(millis(2026, 10, 2), 12.5, 45))
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(AddictionSerializer)
        val text = json.encodeToString(serializer, listOf(a))
        val back = json.decodeFromString(serializer, text).single()
        assertEquals(a.savingsMeta, back.savingsMeta)
        assertEquals(a.relapseCosts, back.relapseCosts)
        assertEquals(a.savings, back.savings)
    }

    @Test
    fun filesWithoutNewFieldsStillLoad() {
        // Written by the original app: no savings_meta / relapse_costs keys.
        val text = """[{"name":"x","status":"Ongoing","history":{"1000":0},"priority":1,""" +
            """"daily_notes":{},"time_saving":"00:00","savings":{"Money":{"first":2.0,"second":"€"}},""" +
            """"milestones":[]}]"""
        val back = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(AddictionSerializer), text).single()
        assertTrue(back.savingsMeta.isEmpty())
        assertTrue(back.relapseCosts.isEmpty())
        assertEquals(Pair(2.0, "€"), back.savings["Money"])
    }

    // ----- Current streak (time since the last relapse) -----

    /** Two attempts: 20 days until a relapse, then a running one of 7 days. */
    private fun twoAttempts(): Pair<Addiction, Long> {
        val first = millis(2026, 9, 1)
        val relapse = first + 20 * day
        val now = relapse + 7 * day
        val history = LinkedHashMap<Long, Long>()
        history[first] = relapse
        history[relapse] = 0
        val a = Addiction("t", Addiction.Status.Ongoing, history, Addiction.Priority.MEDIUM,
            LinkedHashMap(), LocalTime(0, 0), LinkedHashMap(), LinkedHashSet())
        return a to now
    }

    @Test
    fun timePerWeekForAllAttemptsAndTheCurrentStreak() {
        val (a, now) = twoAttempts()
        // 8 hours per week, stored per day like every saving.
        a.savings["Time"] = Pair(8.0 / 7.0, TIME_SAVING_UNIT)
        a.savingsMeta["Time"] = SavingMeta(SavingCategory.TIME, RatePeriod.WEEK, 8.0)
        val s = a.calculateSavings(now, tz)
        assertTrue(s.hasCurrentStreak)
        // 27 clean days in total: 27 / 7 weeks * 8 h; the current streak is exactly one week.
        assertNear(27.0 / 7.0 * 8 * 60, s.timeTotalMinutes!!, 1e-3)
        assertNear(8.0 * 60, s.timeCurrentStreakMinutes!!, 1e-3)
    }

    @Test
    fun currentStreakIgnoresRelapseCosts() {
        val (a, now) = twoAttempts()
        a.addMoney("Cigarettes", 5.0)
        a.relapseCosts += RelapseCost(timeMillis = a.history.keys.last(), money = 12.0)
        val money = a.calculateSavings(now, tz).moneyByCurrency.getValue("€")
        assertNear(27 * 5.0 - 12.0, money.total)
        assertNear(7 * 5.0, money.currentStreak)
    }

    @Test
    fun stoppedAddictionHasNoCurrentStreak() {
        val start = millis(2026, 9, 1)
        val history = LinkedHashMap<Long, Long>()
        history[start] = start + 10 * day
        val a = Addiction("t", Addiction.Status.Stopped, history, Addiction.Priority.MEDIUM,
            LinkedHashMap(), LocalTime(0, 0), LinkedHashMap(), LinkedHashSet())
        a.addMoney("Cigarettes", 5.0)
        val s = a.calculateSavings(start + 30 * day, tz)
        assertTrue(!s.hasCurrentStreak)
        assertNear(50.0, s.moneyByCurrency.getValue("€").total)
        assertNear(0.0, s.moneyByCurrency.getValue("€").currentStreak)
    }
}
