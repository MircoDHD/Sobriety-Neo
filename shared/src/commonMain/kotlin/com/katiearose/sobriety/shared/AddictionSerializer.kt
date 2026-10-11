package com.katiearose.sobriety.shared

import kotlinx.datetime.*
import kotlinx.datetime.serializers.DateTimeUnitSerializer
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.datetime.serializers.LocalDateIso8601Serializer
import kotlinx.datetime.serializers.LocalTimeIso8601Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

/**
 * Converts an [Addiction] to and from JSON.
 *
 * This is hand written (instead of the generated serializer) so that files written by older
 * versions of the app can still be read. **Every user's data is stored with this format**, so be
 * very careful when changing it:
 *  - never change the position or name of an existing element in [descriptor],
 *  - only append new elements at the END of [descriptor] (the index is only used in this file,
 *    the JSON file contains the key names),
 *  - the reader ignores unknown keys, so new fields do not break old files.
 *
 * JSON keys (in the order of their element index):
 * `name, status, history, priority, daily_notes, time_saving, savings, milestones`, the
 * legacy keys `last_relapse, is_stopped`, and the keys added by Sobriety Neo:
 * `savings_meta, relapse_costs, display`.
 *
 * Compatibility: the original Sobriety app ignores the Sobriety Neo keys when it imports a file
 * exported from Sobriety Neo, so that data is simply not shown there.
 */
object AddictionSerializer : KSerializer<Addiction> {
    private val historySerializer = MapSerializer(Long.serializer(), Long.serializer())
    private val dailyNotesSerializer = MapSerializer(LocalDateIso8601Serializer, String.serializer())
    private val savingsSerializer = MapSerializer(String.serializer(), PairSerializer(Double.serializer(), String.serializer()))
    private val milestonesSerializer = SetSerializer(PairSerializer(Int.serializer(), DateTimeUnitSerializer))
    private val savingsMetaSerializer = MapSerializer(String.serializer(), SavingMeta.serializer())
    private val relapseCostsSerializer = ListSerializer(RelapseCost.serializer())

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Addiction =
        decoder.decodeStructure(descriptor) {
            // Defaults are used for every key that is missing in the file.
            var name = ""
            var status = Addiction.Status.Ongoing
            val history = LinkedHashMap<Long, Long>()
            var priority = Addiction.Priority.MEDIUM
            var dailyNotes = LinkedHashMap<LocalDate, String>()
            var timeSaving = LocalTime(0, 0)
            var savings = LinkedHashMap<String, Pair<Double, String>>()
            var milestones = LinkedHashSet<Pair<Int, DateTimeUnit>>()
            var savingsMeta = LinkedHashMap<String, SavingMeta>()
            var relapseCosts = mutableListOf<RelapseCost>()
            var display = DisplaySettings()
            // Only set when the file was written by a very old version (see key 8 below).
            var legacyLastRelapse: Instant? = null
            while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, i)
                    1 -> status = Addiction.Status.valueOf(decodeStringElement(descriptor, i))
                    // putAll rather than reassign for last_relapse legacy key
                    2 -> history.putAll(LinkedHashMap(decodeSerializableElement(descriptor, i, historySerializer)))
                    // A priority outside of the known range falls back to MEDIUM instead of crashing.
                    3 -> priority = Addiction.Priority.values()
                        .getOrElse(decodeIntElement(descriptor, i)) { Addiction.Priority.MEDIUM }
                    4 -> dailyNotes = LinkedHashMap(decodeSerializableElement(descriptor, i, dailyNotesSerializer))
                    5 -> timeSaving = decodeSerializableElement(descriptor, i, LocalTimeIso8601Serializer)
                    6 -> savings = LinkedHashMap(decodeSerializableElement(descriptor, i, savingsSerializer))
                    7 -> milestones = LinkedHashSet(decodeSerializableElement(descriptor, i, milestonesSerializer))

                    // Legacy keys
                    // last_relapse: the single start time that very old versions stored
                    // instead of a history.
                    8 -> {
                        val lastRelapse = decodeSerializableElement(descriptor, i, InstantComponentSerializer)
                        legacyLastRelapse = lastRelapse
                        if (lastRelapse > Clock.System.now()) {
                            status = Addiction.Status.Future
                            history[lastRelapse.toEpochMilliseconds()] = 0
                        }
                    }
                    // is_stopped
                    9 -> if (decodeBooleanElement(descriptor, i)) status = Addiction.Status.Stopped

                    // Sobriety Neo keys
                    10 -> savingsMeta = LinkedHashMap(decodeSerializableElement(descriptor, i, savingsMetaSerializer))
                    11 -> relapseCosts = decodeSerializableElement(descriptor, i, relapseCostsSerializer).toMutableList()
                    12 -> display = decodeSerializableElement(descriptor, i, DisplaySettings.serializer())

                    CompositeDecoder.DECODE_DONE -> break
                }
            }

            // The rest of the app relies on the history having at least one entry (it reads
            // history.keys.last() everywhere). If a file has none, create one instead of crashing
            // later with a NoSuchElementException.
            if (history.isEmpty()) {
                val start = legacyLastRelapse?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds()
                // A stopped addiction needs a finished attempt, so give it a zero-length one.
                history[start] = if (status == Addiction.Status.Stopped) start else 0
            }

            Addiction(name, status, history, priority, dailyNotes, timeSaving, savings, milestones,
                savingsMeta, relapseCosts, display)
        }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("addiction") {
            element<String>("name")
            element<String>("status")
            element<LinkedHashMap<Long, Long>>("history")
            element<Int>("priority")
            element<LinkedHashMap<LocalDate, String>>("daily_notes")
            element<LocalTime>("time_saving")
            element<LinkedHashMap<String, Pair<Double, String>>>("savings")
            element<LinkedHashSet<Pair<Int, DateTimeUnit>>>("milestones")

            // Legacy keys
            // They are only read, never written (see serialize below).
            element<Instant>("last_relapse")
            element<Boolean>("is_stopped")

            // Sobriety Neo keys (always written, read if present)
            element("savings_meta", savingsMetaSerializer.descriptor)
            element("relapse_costs", relapseCostsSerializer.descriptor)
            element("display", DisplaySettings.serializer().descriptor)
        }

    override fun serialize(encoder: Encoder, value: Addiction) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeStringElement(descriptor, 1, value.status.name)
            encodeSerializableElement(descriptor, 2, historySerializer, value.history)
            encodeIntElement(descriptor, 3, value.priority.ordinal)
            encodeSerializableElement(descriptor, 4, dailyNotesSerializer, value.dailyNotes)
            encodeSerializableElement(descriptor, 5, LocalTimeIso8601Serializer, value.timeSaving)
            encodeSerializableElement(descriptor, 6, savingsSerializer, value.savings)
            encodeSerializableElement(descriptor, 7, milestonesSerializer, value.milestones)
            // 8 and 9 are the legacy keys, which are never written.
            encodeSerializableElement(descriptor, 10, savingsMetaSerializer, value.savingsMeta)
            encodeSerializableElement(descriptor, 11, relapseCostsSerializer, value.relapseCosts)
            encodeSerializableElement(descriptor, 12, DisplaySettings.serializer(), value.display)
        }
    }
}
