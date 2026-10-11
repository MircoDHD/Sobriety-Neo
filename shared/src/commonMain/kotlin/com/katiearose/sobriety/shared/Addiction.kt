package com.katiearose.sobriety.shared

import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One tracked addiction / habit and everything the app stores about it.
 *
 * This is the central data class. It is saved to disk as JSON through [AddictionSerializer], so
 * **do not rename or reorder fields without also updating the serializer**, otherwise existing
 * user data can no longer be read.
 *
 * @property name unique display name; also used as the key when importing data
 * @property status whether the addiction is currently being tracked, was stopped, or starts later
 * @property history all attempts, see below
 * @property priority only used to sort the main list and to color the label
 * @property dailyNotes one free-text note per calendar day
 * @property timeSaving time that is saved per day by not doing the addiction (00:00 = not set)
 * @property savings other things that are saved per day: name -> (amount per day, unit)
 * @property milestones goals such as "7 days" or "3 months"
 * @property savingsMeta (Sobriety Neo) category and entered rate of each saving, see SavingsCalculator.kt
 * @property relapseCosts (Sobriety Neo) money and time spent during relapses, see SavingsCalculator.kt
 */
@Serializable(with = AddictionSerializer::class)
@SerialName("addiction")
data class Addiction(
    val name: String,
    var status: Status,
    // Maps past, current, and future attempts start and end epoch milliseconds.
    // An end value of 0 means "this attempt is still running" (or has not started yet).
    // The map keeps its insertion order, so the LAST entry is always the newest attempt and
    // history.keys.last() is the start of the current attempt (= the last relapse).
    val history: LinkedHashMap<Long, Long>,
    var priority: Priority,
    val dailyNotes: LinkedHashMap<LocalDate, String>,
    var timeSaving: LocalTime,
    // What is being saved -> Pair(amount, unit)
    val savings: LinkedHashMap<String, Pair<Double, String>>,
    val milestones: LinkedHashSet<Pair<Int, DateTimeUnit>>,
    // Added by Sobriety Neo. They have default values so that older code paths and files that do
    // not contain them keep working.
    val savingsMeta: LinkedHashMap<String, SavingMeta> = LinkedHashMap(),
    val relapseCosts: MutableList<RelapseCost> = mutableListOf(),
) {

    /** How important this addiction is to the user. The order of the entries is the sort order. */
    enum class Priority {
        HIGH, MEDIUM, LOW
    }

    /**
     * - [Ongoing]: the last attempt is running and the counter counts up
     * - [Stopped]: the user stopped tracking; the last history entry has an end time
     * - [Future]: the start time of the last attempt lies in the future and is still waiting
     */
    enum class Status {
        Ongoing, Stopped, Future
    }

    /**
     * Average duration of the given attempts.
     *
     * @param whichAttempts list of map indices (positions in [history], starting at 0)
     * @return the average in milliseconds, or 0 if the list is empty
     */
    fun calculateAvgRelapseDuration(whichAttempts: List<Int>): Long {
        // Guard against a division by zero (would crash with an ArithmeticException).
        if (whichAttempts.isEmpty()) return 0L
        var totalDuration = 0L
        val list = history.toList()
        for (attemptIndex in whichAttempts) {
            totalDuration += (list[attemptIndex].second - list[attemptIndex].first)
        }
        return totalDuration / whichAttempts.size
    }

    /**
     * Average duration of the most recent finished attempts.
     *
     * @param numAttempts number of attempts to calculate
     * @return average duration in milliseconds or null if no history
     */
    fun calculateRecentAverage(numAttempts: Int): Long? {
        val maxExclusive = when (status) {
            // Do not include unfinished attempt
            Status.Ongoing, Status.Future -> history.size - 1
            // Stopped addictions have the final full attempt at the end
            Status.Stopped -> history.size
        }
        val minimum = (maxExclusive - numAttempts).coerceAtLeast(0)
        val range = (minimum until maxExclusive).toList()
        return if (range.isNotEmpty())
            calculateAvgRelapseDuration(range)
        else
            null
    }

    /**
     * The moment in time that progress is measured against: "now" while the addiction is running,
     * the stop time once it was stopped, and the (future) start for a waiting addiction.
     */
    private fun referenceMillis(): Long = when (status) {
        Status.Ongoing -> Clock.System.now().toEpochMilliseconds()
        Status.Stopped -> history.values.last()
        Status.Future -> history.keys.last()
    }

    /**
     * The moment a milestone is reached, counted from the start of the current attempt.
     *
     * Calendar arithmetic is used, so "1 month" really ends on the same day of the next month
     * (and "1 day" on the same time of the next day, also across daylight saving changes). This is
     * the same way the counter on the main screen counts. The old code used a fixed 30.4375 days
     * per month, which made milestones end a few hours away from where the counter said
     * "1 month".
     *
     * @return epoch milliseconds, or [Long.MAX_VALUE] if the value is so large that it can not be
     *   represented (the UI limits the input, this is only a safety net against a crash)
     */
    fun milestoneGoalMillis(milestone: Pair<Int, DateTimeUnit>): Long = try {
        Instant.fromEpochMilliseconds(history.keys.last())
            .plus(milestone.first, milestone.second, TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /**
     * @return a pair of
     * - the goal point in milliseconds
     * - the progress as an int in range 0..100
     */
    fun calculateMilestoneProgressionPercentage(milestone: Pair<Int, DateTimeUnit>): Pair<Long, Int> {
        val start = history.keys.last()
        val goal = milestoneGoalMillis(milestone)
        // Waiting addictions have not made any progress yet.
        if (status == Status.Future) return Pair(goal, 0)

        val total = (goal - start).toDouble()
        val done = (referenceMillis() - start).toDouble()
        val percent = if (total <= 0.0) 100 else ((done / total) * 100).toInt()
        // Never report more than 100% (the progress bar would overflow) or less than 0%.
        return Pair(goal, percent.coerceIn(0, 100))
    }

    /**
     * Ends tracking for good ("I stop using this app for this addiction").
     * The current attempt is closed with the current time and the status becomes [Status.Stopped].
     */
    fun stopAbstaining() {
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(Clock.System.now().toEpochMilliseconds())

            // You can't stop twice
            Status.Stopped -> {}

            // If a Future addiction is stopped, remove the future attempt
            // If that was the only attempt, add in a 0-length attempt to reflect history
            Status.Future -> {
                history.remove(history.keys.last())
                if (history.isEmpty()) {
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    history[currentTime] = currentTime
                }
            }
        }
        status = Status.Stopped
    }

    /**
     * Records a relapse: closes the running attempt and immediately starts a new one.
     * Also used to start tracking again after [stopAbstaining] or to start a waiting
     * ([Status.Future]) addiction early.
     *
     * @param cost optional money / time spent during this relapse. It is stored in
     *   [relapseCosts] (with the current time) and later subtracted from the savings.
     */
    fun relapse(cost: RelapseCost? = null) {
        val now = Clock.System.now().toEpochMilliseconds()
        // Only a real relapse (of a running attempt) can cost something.
        if (status == Status.Ongoing && cost != null && (cost.money > 0.0 || cost.minutes > 0L)) {
            relapseCosts.add(cost.copy(timeMillis = now))
        }
        when (status) {
            // End the current attempt
            Status.Ongoing -> history.putLast(Clock.System.now().toEpochMilliseconds())

            Status.Stopped -> {}

            // Remove the future attempt (user is starting early)
            Status.Future -> history.remove(history.keys.last())
        }
        history[Clock.System.now().toEpochMilliseconds()] = 0
        status = Status.Ongoing
    }

    /**
     * Turns a waiting ([Status.Future]) addiction into a running one once its start time has
     * passed. This is called regularly by the UI (once per second) and when the data is loaded,
     * so it also works if the app was closed when the start time was reached.
     *
     * @return true if the status was changed, so that the caller knows it has to save the data
     */
    fun activateIfDue(nowMillis: Long): Boolean {
        if (status == Status.Future && history.keys.last() <= nowMillis) {
            status = Status.Ongoing
            return true
        }
        return false
    }

    /** Notes as a list, sorted by date according to [sort]. */
    fun getDailyNotesList(sort: SortMode): List<Pair<LocalDate, String>> {
        return when (sort) {
            SortMode.ASC -> dailyNotes.toList().sortedWith { n1, n2 -> n1.first.compareTo(n2.first) }
            SortMode.DESC -> dailyNotes.toList().sortedWith { n1, n2 -> n2.first.compareTo(n1.first) }
            SortMode.NONE -> dailyNotes.toList()
        }
    }

    /**
     * Milestones as a list.
     *
     * @param sort sorted by their nominal length ("2 weeks" before "1 month")
     * @param hideComplete if true, milestones that were already reached are left out
     */
    fun getMilestonesList(sort: SortMode, hideComplete: Boolean): List<Pair<Int, DateTimeUnit>> {
        var list = when (sort) {
            SortMode.ASC -> milestones.toList().sortedWith { m1, m2 ->
                (m1.first * m1.second.toMillis()).compareTo(m2.first * m2.second.toMillis())
            }
            SortMode.DESC -> milestones.toList().sortedWith { m1, m2 ->
                (m2.first * m2.second.toMillis()).compareTo(m1.first * m1.second.toMillis())
            }
            SortMode.NONE -> milestones.toList()
        }
        if (hideComplete) {
            val reference = referenceMillis()
            list = list.filter { reference < milestoneGoalMillis(it) }
        }
        return list
    }

    companion object {
        /**
         * Creates a new addiction whose first attempt starts at [millis].
         * If that moment is still in the future the status is [Status.Future].
         */
        fun newInstance(name: String, millis: Long, priority: Priority): Addiction {
            val history = LinkedHashMap<Long, Long>()
            history[millis] = 0
            val status =
                if (millis > Clock.System.now().toEpochMilliseconds()) Status.Future
                else Status.Ongoing
            return  Addiction(
                name,
                status,
                history,
                priority,
                LinkedHashMap(),
                LocalTime(0, 0),
                LinkedHashMap(),
                LinkedHashSet()
            )
        }
    }
}
