package com.filestech.agenda_tech.domain.ics

import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.RecurrenceFreq
import com.filestech.agenda_tech.domain.model.RecurrenceRule
import com.filestech.agenda_tech.domain.model.Weekday
import com.filestech.agenda_tech.domain.recurrence.monthlyRulesThrough
import java.time.LocalDate

/** What an RFC 5545 `RRULE` means to this app. */
internal sealed interface RRuleReading {

    /** A rule the expander follows date for date. */
    data class Reproduced(val rule: RecurrenceRule) : RRuleReading

    /**
     * A repeating event whose rule this app cannot follow — an unknown `FREQ` (`HOURLY`), a part such as
     * `BYWEEKNO`, a list of ranked weekdays… It is imported as its first date only, and counted, so the
     * import can say so instead of showing a series on dates its author never chose.
     */
    data object FirstDateOnly : RRuleReading
}

/**
 * The one reading of an `RRULE` value, shared by the `.ics` import ([IcsCodec]) and the device-calendar
 * import ([com.filestech.agenda_tech.domain.device.DeviceEventMapper]).
 *
 * ## Why one, not two
 *
 * Each import had its own parser, and they had already drifted: the codec matched `BYDAY` tokens exactly,
 * so `2TU` was dropped; the device mapper kept their first two letters, so `2TU` became `TU`. Neither knew
 * `BYMONTHDAY`, `BYSETPOS` or `BYMONTH`, and both returned a rule anyway. The same "second Tuesday of the
 * month" therefore arrived as "the 10th of every month" — the day number of its start — and
 * `FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR` as every day of the week, weekends included. Silently, by both paths.
 *
 * What stays with each caller is what genuinely differs between them: how an `UNTIL` stamp is read (the
 * codec knows the event's `TZID` parameters, the provider column is a bare stamp) and where the `EXDATE`s
 * come from (lines of their own in a file, one column in the provider).
 *
 * ## What it reproduces
 *
 * - `DAILY`, `WEEKLY` (on chosen days), `MONTHLY`, `YEARLY`, with `INTERVAL`, `COUNT` or `UNTIL`;
 * - `MONTHLY` by position: `BYDAY=2TU`, `BYDAY=-1FR`, or Outlook's `BYDAY=TU;BYSETPOS=2`; the last day,
 *   `BYMONTHDAY=-1`; and `BYMONTHDAY` naming the start's own day, which is the plain monthly rule;
 * - `DAILY;BYDAY=MO,TU,WE,TH,FR` (every day, but only those): the weekly rule on those days;
 * - `YEARLY` with `BYMONTH` naming the start's month, alone or with `BYMONTHDAY` naming its day, as
 *   Outlook writes it.
 *
 * Anything else that selects dates is refused as a whole ([RRuleReading.FirstDateOnly]), never read in
 * part: a rule part ignored is a series on the wrong dates. So are bounds it cannot read exactly — an
 * `INTERVAL` out of range, a `COUNT` or `UNTIL` it cannot read — and more cancelled occurrences than
 * [RecurrenceRule.MAX_EXDATES]. `WKST` is ignored where it changes nothing, which is everywhere but one
 * case (see [spansWeekStart]).
 *
 * ## What it never does
 *
 * Throw. The input is untrusted on both paths, and an exception here used to strip the recurrence from
 * an otherwise fine event without a word.
 */
internal object RRuleReader {

    private val WEEKDAYS = mapOf(
        "MO" to Weekday.MONDAY, "TU" to Weekday.TUESDAY, "WE" to Weekday.WEDNESDAY, "TH" to Weekday.THURSDAY,
        "FR" to Weekday.FRIDAY, "SA" to Weekday.SATURDAY, "SU" to Weekday.SUNDAY,
    )

    /** `BYDAY` item: an optional signed rank, then a weekday — `TU`, `2TU`, `+2TU`, `-1FR`. */
    private val DAY_ITEM = Regex("([+-]?\\d{1,2})?([A-Z]{2})")

    /**
     * The rule parts this reader understands. Every other one — `BYWEEKNO`, `BYYEARDAY`, `BYHOUR`,
     * `BYMINUTE`, `BYSECOND`, RFC 7529's `RSCALE` and `SKIP`, or a name it has never heard of — selects or
     * moves dates in a way the expander cannot follow, so its presence alone refuses the rule.
     */
    private val UNDERSTOOD = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "WKST", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS")

    /**
     * Reads [value] (the text after `RRULE:`) for a series that starts on [start] — its date in the zone
     * the series will be expanded in, which is what a position in the month is counted against.
     *
     * [parseUntil] reads an `UNTIL` stamp the caller's way, returning null when it cannot; [exDatesUtcMillis]
     * are the caller's cancelled occurrences, carried into the rule — up to [RecurrenceRule.MAX_EXDATES].
     */
    fun read(
        value: String,
        start: LocalDate,
        exDatesUtcMillis: List<Long>,
        parseUntil: (String) -> Long?,
    ): RRuleReading {
        // Refused whole rather than cut down to the cap: a cancellation dropped is an occurrence brought
        // back. Duplicates are one cancellation, so they are counted once (and stored once).
        val exDates = exDatesUtcMillis.distinct().takeIf { it.size <= RecurrenceRule.MAX_EXDATES }
            ?: return RRuleReading.FirstDateOnly
        return runCatching { ruleOrNull(value, start, exDates, parseUntil) }
            .getOrNull()
            ?.let(RRuleReading::Reproduced)
            ?: RRuleReading.FirstDateOnly
    }

    private fun ruleOrNull(
        value: String,
        start: LocalDate,
        exDates: List<Long>,
        parseUntil: (String) -> Long?,
    ): RecurrenceRule? {
        val parts = partsOf(value)?.takeIf { parts -> parts.keys.all { it in UNDERSTOOD } } ?: return null
        val interval = intervalOf(parts) ?: return null
        val bounds = boundsOf(parts, parseUntil) ?: return null
        return shapeOf(parts, interval, start)?.let { shape ->
            RecurrenceRule(
                freq = shape.freq,
                interval = interval,
                byWeekdays = shape.byWeekdays,
                count = bounds.count,
                untilUtcMillis = bounds.untilUtcMillis,
                exDatesUtcMillis = exDates,
                monthly = shape.monthly,
            )
        }
    }

    /**
     * `INTERVAL`, 1 when absent; null when present but not a whole number in 1..[RecurrenceRule.MAX_INTERVAL].
     *
     * Both imports used to clamp it — `INTERVAL=1000` became 999, `INTERVAL=0` became 1 — which is a series
     * on other dates than the file's. Audit F1's concern, an interval that made the expander throw, is met
     * just as well by refusing: a first date only never throws either.
     */
    private fun intervalOf(parts: Map<String, String>): Int? {
        val text = parts["INTERVAL"] ?: return 1
        return text.toIntOrNull()?.takeIf { it in 1..RecurrenceRule.MAX_INTERVAL }
    }

    /** The end of a series: at most one of the two, both null for an endless one. */
    private class Bounds(val count: Int?, val untilUtcMillis: Long?)

    /**
     * The series' end, or null when the rule names one this reader cannot read — then the series would run
     * on for ever where the file meant it to stop.
     *
     * COUNT and UNTIL together are not a valid RRULE; a valid COUNT wins, so the same file reads the same
     * way through either import. A COUNT that is not a whole number ≥ 1 is no bound, and it must not take a
     * perfectly good UNTIL down with it — that once turned a finite series infinite. Alone, though, it is
     * refused: `COUNT=0` is a set holding the start only (RFC 5545), and `COUNT=abc` says nothing. An UNTIL
     * that decides the end but cannot be read is refused too: read as no bound, the series never ended.
     */
    private fun boundsOf(parts: Map<String, String>, parseUntil: (String) -> Long?): Bounds? {
        val countText = parts["COUNT"]
        val untilText = parts["UNTIL"]
        val count = countText?.toIntOrNull()?.takeIf { it >= 1 }
        val until = if (count == null) untilText?.let(parseUntil) else null
        // No bound read, though the rule named one: an unreadable UNTIL, or a COUNT that is no bound.
        val namedButUnread = count == null && until == null && (countText != null || untilText != null)
        return if (namedButUnread) null else Bounds(count, until)
    }

    /** The frequency and the days the rule selects, or null when this app cannot follow them. */
    private fun shapeOf(parts: Map<String, String>, interval: Int, start: LocalDate): Shape? {
        val freq = RecurrenceFreq.entries.firstOrNull { it.name == parts["FREQ"] } ?: return null
        val selection = Selection.of(parts) ?: return null
        return when (freq) {
            RecurrenceFreq.DAILY -> selection.daily(interval)
            RecurrenceFreq.WEEKLY -> selection.weekly(interval, start)
            RecurrenceFreq.MONTHLY -> selection.monthly(start)
            RecurrenceFreq.YEARLY -> selection.yearly(start)
        }
    }

    /**
     * `NAME=value` pairs, names and values upper-cased (`Locale.ROOT`: Kotlin's `uppercase()` is
     * locale-free, so a Turkish phone reads `byday` as `BYDAY`). Null for a malformed value — a part
     * without `=`, an empty value, or a part given twice, which RFC 5545 forbids and which leaves no way to
     * tell which one the author meant.
     */
    private fun partsOf(value: String): Map<String, String>? {
        val parts = LinkedHashMap<String, String>()
        for (token in value.split(';').map(String::trim).filter(String::isNotEmpty)) {
            val name = token.substringBefore('=', missingDelimiterValue = "").trim().uppercase()
            val text = token.substringAfter('=', missingDelimiterValue = "").trim().uppercase()
            if (name.isEmpty() || text.isEmpty() || parts.put(name, text) != null) return null
        }
        return parts
    }

    /** One `BYDAY` item: [rank] is null for a plain weekday. */
    private data class DayItem(val rank: Int?, val weekday: Weekday)

    /** What a rule turns into here, before its bounds and cancellations are added. */
    private data class Shape(
        val freq: RecurrenceFreq,
        val byWeekdays: Set<Weekday> = emptySet(),
        val monthly: MonthlyRule = MonthlyRule.SameDay,
    )

    /** The `BY…` parts, read but not yet judged: null for each one absent. */
    private class Selection(
        val byDay: List<DayItem>?,
        val byMonthDay: List<Int>?,
        val byMonth: List<Int>?,
        val bySetPos: List<Int>?,
        val weekStart: Weekday,
    ) {
        /**
         * `DAILY` alone, or `DAILY;BYDAY=MO,TU,WE,TH,FR`: every day, kept to those days — which is the weekly
         * rule on those days as long as no day is skipped (`INTERVAL=1`). A rank under `DAILY` is invalid
         * (RFC 5545 §3.3.10), and so is every other `BY…` here.
         */
        fun daily(interval: Int): Shape? = when {
            byMonthDay != null || byMonth != null || bySetPos != null -> null
            byDay == null -> Shape(RecurrenceFreq.DAILY)
            interval == 1 && byDay.all { it.rank == null } ->
                Shape(RecurrenceFreq.WEEKLY, byWeekdays = byDay.map { it.weekday }.toSet())
            else -> null
        }

        /** `WEEKLY`, on the start's weekday or on plain `BYDAY` weekdays — ranked ones are invalid here. */
        fun weekly(interval: Int, start: LocalDate): Shape? {
            if (byMonthDay != null || byMonth != null || bySetPos != null) return null
            if (byDay.orEmpty().any { it.rank != null }) return null
            val days = byDay.orEmpty().map { it.weekday }.toSet()
            val withStart = days + Weekday.fromIso(start.dayOfWeek.value)
            return if (interval > 1 && spansWeekStart(withStart, weekStart)) null else Shape(RecurrenceFreq.WEEKLY, days)
        }

        /**
         * `MONTHLY` on the start's day number, by position, or on the last day — and only a rule that has
         * its own start among its dates. RFC 5545 leaves a series whose `DTSTART` does not match its rule
         * undefined, no calendar app writes one, and the editor derives the position from the start: a
         * rule that disagreed with it would be rewritten the first time the event is saved.
         */
        fun monthly(start: LocalDate): Shape? {
            val day = byDay?.singleOrNull()
            val rule = when {
                byMonth != null -> null
                byDay == null && bySetPos == null -> when (byMonthDay) {
                    null, listOf(start.dayOfMonth) -> MonthlyRule.SameDay
                    listOf(MonthlyRule.LAST) -> MonthlyRule.LastDay
                    else -> null
                }
                // Every other shape takes exactly one weekday and no day number.
                byMonthDay != null || day == null -> null
                // `BYDAY=2TU`: one weekday, ranked.
                bySetPos == null -> day.rank?.let { position(it, day.weekday) }
                // Outlook's `BYDAY=TU;BYSETPOS=2`: one plain weekday, one position among its dates.
                day.rank == null -> bySetPos.singleOrNull()?.let { position(it, day.weekday) }
                else -> null
            }
            return rule?.takeIf { it in monthlyRulesThrough(start) }?.let { Shape(RecurrenceFreq.MONTHLY, monthly = it) }
        }

        /**
         * `YEARLY`, on the start's day — `BYMONTH` accepted where it repeats the start's month, and
         * `BYMONTHDAY` only beside it, as Outlook writes a yearly event. Alone, `BYMONTHDAY` is not the
         * start's day once a year: RFC 5545 expands it within the year, so `FREQ=YEARLY;BYMONTHDAY=15` is
         * the 15th of EVERY month.
         */
        fun yearly(start: LocalDate): Shape? {
            val sameMonth = byMonth == listOf(start.monthValue)
            val accepted = byDay == null && bySetPos == null &&
                (byMonth == null || sameMonth) &&
                (byMonthDay == null || (sameMonth && byMonthDay == listOf(start.dayOfMonth)))
            return Shape(RecurrenceFreq.YEARLY).takeIf { accepted }
        }

        /** "The [rank]-th [weekday]", when this app can follow it: 1 to 5, or the last one. */
        private fun position(rank: Int, weekday: Weekday): MonthlyRule? =
            if (rank in 1..MonthlyRule.MAX_ORDINAL || rank == MonthlyRule.LAST) MonthlyRule.NthWeekday(rank, weekday) else null

        companion object {
            /** Null when any `BY…` value is malformed: half a selection would be a series on other dates. */
            fun of(parts: Map<String, String>): Selection? {
                val byDay = parts["BYDAY"]?.let { items(it, ::dayItem) }
                val byMonthDay = parts["BYMONTHDAY"]?.let { items(it, String::toIntOrNull) }
                val byMonth = parts["BYMONTH"]?.let { items(it, String::toIntOrNull) }
                val bySetPos = parts["BYSETPOS"]?.let { items(it, String::toIntOrNull) }
                val weekStart = parts["WKST"]?.let(WEEKDAYS::get)
                val read = mapOf(
                    "BYDAY" to byDay, "BYMONTHDAY" to byMonthDay, "BYMONTH" to byMonth,
                    "BYSETPOS" to bySetPos, "WKST" to weekStart,
                )
                if (read.any { (name, value) -> name in parts && value == null }) return null
                return Selection(byDay, byMonthDay, byMonth, bySetPos, weekStart ?: Weekday.MONDAY)
            }

            private fun <T> items(text: String, parse: (String) -> T?): List<T>? =
                text.split(',').map { parse(it.trim()) ?: return null }.takeIf { it.isNotEmpty() }

            private fun dayItem(text: String): DayItem? {
                val match = DAY_ITEM.matchEntire(text) ?: return null
                val weekday = WEEKDAYS[match.groupValues[2]] ?: return null
                val rank = match.groupValues[1].takeIf { it.isNotEmpty() }?.removePrefix("+")?.toInt()
                return if (rank == 0) null else DayItem(rank, weekday)
            }
        }
    }

    /**
     * True when [days] fall on both sides of [weekStart] — the one case where `WKST` moves dates.
     *
     * A series every other week (`INTERVAL=2`) groups its days into weeks before skipping one, and the
     * expander's weeks begin on Monday (`WKST=MO`, the RFC 5545 default). With `WKST=SU`, Sunday joins the
     * following Monday instead of the preceding one, so `INTERVAL=2;BYDAY=MO,SU` gives other dates — RFC
     * 5545 §3.3.10 shows exactly this. Days that all sit on one side of the boundary group the same way
     * under either start, which covers what calendars actually write (`WKST=SU;BYDAY=TU,TH`). The start's
     * own weekday is passed in too: it decides which week is the first.
     */
    private fun spansWeekStart(days: Set<Weekday>, weekStart: Weekday): Boolean =
        weekStart != Weekday.MONDAY &&
            days.any { it.isoValue >= weekStart.isoValue } &&
            days.any { it.isoValue < weekStart.isoValue }
}
