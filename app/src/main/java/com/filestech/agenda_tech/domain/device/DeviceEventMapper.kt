package com.filestech.agenda_tech.domain.device

import com.filestech.agenda_tech.core.text.BidiSanitizer
import com.filestech.agenda_tech.core.time.DAY_MILLIS
import com.filestech.agenda_tech.core.time.TimeZones
import com.filestech.agenda_tech.domain.ics.RRuleReader
import com.filestech.agenda_tech.domain.ics.RRuleReading
import com.filestech.agenda_tech.domain.ics.RfcDuration
import com.filestech.agenda_tech.domain.model.CalendarColor
import com.filestech.agenda_tech.domain.model.DeviceEvent
import com.filestech.agenda_tech.domain.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pure mapper: a [DeviceEvent] read from the device calendar → a domain [Event]. No Android
 * dependency, so every branch (duration fallback, RRULE/EXDATE parsing, all-day, colour matching,
 * Bidi stripping) is unit-testable with plain values.
 *
 * Deliberately tolerant: a row that can't be mapped (blank title, unparseable start) returns null
 * and is skipped rather than aborting the whole import.
 */
object DeviceEventMapper {

    private const val DEFAULT_DURATION_MILLIS = 60L * 60 * 1000 // 1h when nothing else is known

    // Les bornes des durées pathologiques (un agenda tiers peut en porter) vivent desormais dans
    // RfcDuration, partage avec IcsCodec — un seul plafond, jamais une seconde copie du nombre
    // (audit AG-1). La longueur des champs, elle, reste plafonnee par BidiSanitizer.stripAndCap.

    /** Compact iCalendar timestamps: `20250131T090000Z` (UTC) or `20250131T090000` (floating/local). */
    private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * A device row as the import takes it in: the [event], and whether it repeats by a rule this app
     * cannot reproduce — then [Event.recurrence] is null and the event stands for its first date
     * ([RRuleReading.FirstDateOnly]), which the import counts to tell the user.
     */
    data class Mapped(val event: Event, val firstDateOnly: Boolean = false)

    fun map(device: DeviceEvent, calendarId: Long, defaultZone: ZoneId = ZoneId.systemDefault()): Mapped? {
        val title = device.title?.let(::sanitize)?.trim().orEmpty()
        if (title.isEmpty()) return null

        // Time model. All-day events are stored by the provider at UTC midnight; the rest of the app
        // anchors all-day to the *device* zone (local midnight, exclusive end). Re-anchor here so an
        // all-day holiday lands on its real calendar day instead of drifting by the UTC offset
        // (e.g. "Fête nationale" showing on the 15th instead of the 14th).
        val start: Long
        val end: Long
        val zoneId: ZoneId
        if (device.allDay) {
            val date = Instant.ofEpochMilli(device.dtStartUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val days = device.dtEndUtcMillis
                ?.let { ((it - device.dtStartUtcMillis) / DAY_MILLIS).coerceAtLeast(1) }
                ?: 1
            zoneId = defaultZone
            start = date.atStartOfDay(defaultZone).toInstant().toEpochMilli()
            end = date.plusDays(days).atStartOfDay(defaultZone).toInstant().toEpochMilli()
        } else {
            // Audit F3 — one resolver for every ingestion path. This one already validated the zone
            // (it was the only one that did), but with its own rules: a device row synced from
            // Exchange carries a Windows zone name, which it read as the device zone.
            zoneId = TimeZones.resolve(device.eventTimeZone, defaultZone)
            start = device.dtStartUtcMillis
            end = device.dtEndUtcMillis
                ?: device.durationRfc
                    ?.let(RfcDuration::parseMillis)
                    ?.takeIf { it > 0 }
                    ?.let { device.dtStartUtcMillis + it }
                ?: (device.dtStartUtcMillis + DEFAULT_DURATION_MILLIS)
            if (end < start) return null
        }

        val reading = device.rrule?.let { readRRule(it, device.exDate, start, zoneId) }
        val event = Event(
            calendarId = calendarId,
            title = title,
            description = device.description?.let(::sanitize)?.ifBlank { null },
            location = device.location?.let(::sanitize)?.ifBlank { null },
            startUtcMillis = start,
            endUtcMillis = end,
            timeZoneId = zoneId.id,
            allDay = device.allDay,
            recurrence = (reading as? RRuleReading.Reproduced)?.rule,
            sourceUid = device.uid,
        )
        return Mapped(event, firstDateOnly = reading == RRuleReading.FirstDateOnly)
    }

    /**
     * Fallback uid for an event with no sync id (local calendar, or created while offline and not yet
     * pushed). The import matches on this form too, to catch the `rowid → sync-id` transition and
     * avoid re-inserting the event once it finally syncs.
     */
    fun rowIdUid(eventRowId: Long): String = "rowid:$eventRowId"

    /** Nearest colour in the closed palette to a device ARGB (Euclidean in RGB); DEFAULT if null. */
    fun nearestColor(argb: Int?): CalendarColor {
        if (argb == null) return CalendarColor.DEFAULT
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return CalendarColor.entries.minBy { c ->
            val cr = (c.argb shr 16) and 0xFF
            val cg = (c.argb shr 8) and 0xFF
            val cb = c.argb and 0xFF
            val dr = r - cr; val dg = g - cg; val db = b - cb
            dr * dr + dg * dg + db * db
        }
    }

    // Anti-Bidi spoofing + length cap against a hostile provider — the single shared guard, so the
    // two import paths (.ics and device) can never drift apart.
    private fun sanitize(text: String): String = BidiSanitizer.stripAndCap(text)

    /**
     * The provider's `RRULE` column through the reader both imports share ([RRuleReader]), with the
     * `EXDATE` column folded in; null when the row does not repeat. What stays here is what only the
     * provider has: an `UNTIL` that is a bare stamp, and one `EXDATE` column for the whole list.
     *
     * The rule's dates are counted on the start's date in [zone], the zone the series is stored and
     * expanded in.
     */
    private fun readRRule(rrule: String, exDate: String?, startUtcMillis: Long, zone: ZoneId): RRuleReading? {
        val value = rrule.substringAfter("RRULE:", rrule).takeIf { it.isNotBlank() } ?: return null
        val startDate = Instant.ofEpochMilli(startUtcMillis).atZone(zone).toLocalDate()
        return RRuleReader.read(value, startDate, parseExDates(exDate, zone)) { parseStamp(it, zone) }
    }

    private fun parseExDates(exDate: String?, zone: ZoneId): List<Long> {
        if (exDate.isNullOrBlank()) return emptyList()
        // The EXDATE column may carry a `TZID=...:` prefix before a comma-separated stamp list.
        val payload = exDate.substringAfterLast(':')
        return payload.split(",")
            .mapNotNull { runCatching { parseStamp(it.trim(), zone) }.getOrNull() }
    }

    /** Parses a compact iCalendar date/date-time (`…Z`, floating, or date-only) to epoch-millis. */
    private fun parseStamp(raw: String, zone: ZoneId): Long? {
        val s = raw.trim()
        return when {
            s.isEmpty() -> null
            s.endsWith("Z") ->
                runCatching { LocalDateTime.parse(s, UTC_STAMP).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            s.contains("T") ->
                runCatching { LocalDateTime.parse(s, LOCAL_STAMP).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            else ->
                runCatching { LocalDate.parse(s, DATE_STAMP).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}
