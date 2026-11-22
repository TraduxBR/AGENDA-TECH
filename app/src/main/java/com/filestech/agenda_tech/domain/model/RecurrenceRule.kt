package com.filestech.agenda_tech.domain.model

import timber.log.Timber

/**
 * A recurrence rule attached to an [Event], modelled on the subset of RFC 5545 `RRULE` an
 * agenda needs. A null recurrence on the event means "single occurrence".
 *
 * Invariants (enforced in [init]):
 *  - [interval] ≥ 1.
 *  - [count] and [untilUtcMillis] are mutually exclusive (an RRULE has at most one bound).
 *  - [monthly] is [MonthlyRule.SameDay] unless [freq] is [RecurrenceFreq.MONTHLY]: a position in the
 *    month means nothing to another frequency, and accepting one would let two rows that describe the
 *    same series compare unequal.
 *  - [byWeekdays] is only meaningful for [RecurrenceFreq.WEEKLY]; it is ignored otherwise.
 *
 * [exDatesUtcMillis] holds the start instants of cancelled occurrences (RFC 5545 `EXDATE`) —
 * "delete this one occurrence" without breaking the series.
 *
 * NOTE (phase 2 — NOT implemented in the scaffold): the actual expansion of a rule into concrete
 * occurrence instants (respecting time zone / DST) and the RFC 5545 text (de)serialisation for
 * `.ics` interop live in a dedicated `RecurrenceExpander` / `IcsCodec`. This value object stays a
 * pure, structured description — no expansion logic here.
 */
data class RecurrenceRule(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val byWeekdays: Set<Weekday> = emptySet(),
    val count: Int? = null,
    val untilUtcMillis: Long? = null,
    val exDatesUtcMillis: List<Long> = emptyList(),
    /** Which day of each month a [RecurrenceFreq.MONTHLY] series falls on; see [MonthlyRule]. */
    val monthly: MonthlyRule = MonthlyRule.SameDay,
) {
    init {
        require(interval in 1..MAX_INTERVAL) {
            "RRULE interval must be in 1..$MAX_INTERVAL, was $interval"
        }
        require(count == null || untilUtcMillis == null) {
            "RRULE cannot set both COUNT and UNTIL"
        }
        require(count == null || count >= 1) { "RRULE count must be >= 1, was $count" }
        require(monthly == MonthlyRule.SameDay || freq == RecurrenceFreq.MONTHLY) {
            "a position in the month ($monthly) needs FREQ=MONTHLY, was $freq"
        }
    }

    /** True when the rule is open-ended (neither a COUNT nor an UNTIL bound). */
    val isInfinite: Boolean get() = count == null && untilUtcMillis == null

    /**
     * The same rule with [instantUtcMillis] cancelled (RFC 5545 `EXDATE`) — what "delete this one
     * occurrence" and "move this one occurrence" both do to the master.
     *
     * Idempotent: excluding an already-excluded instant returns the rule unchanged, so a re-save
     * cannot grow a list of duplicates — and that list is exported verbatim to `.ics`.
     */
    fun excluding(instantUtcMillis: Long): RecurrenceRule =
        if (instantUtcMillis in exDatesUtcMillis) {
            this
        } else {
            copy(exDatesUtcMillis = exDatesUtcMillis + instantUtcMillis)
        }

    companion object {
        /**
         * Upper bound on [interval] (audit F1/F5/F7).
         *
         * The expander turns an interval into a year offset, and `YearMonth.of` throws for a year
         * outside +/-999,999,999 — an uncaught exception on the render path of every calendar view
         * and of the widget, which made one imported event enough to crash the app on every launch
         * with no in-app way to reach and delete it. The editor always clamped; the import paths did
         * not. The bound lives here so no future ingestion path can reintroduce the hole, and it is
         * enforced in [init] rather than left to callers.
         */
        const val MAX_INTERVAL = 999

        /**
         * Most cancelled occurrences ([exDatesUtcMillis]) an IMPORT takes in one rule.
         *
         * They are stored as one comma-separated TEXT cell. A crafted 5 MiB `.ics` can carry some 300 000
         * distinct EXDATEs: a cell over 2 MB, past what an Android CursorWindow holds, so every query that
         * reads the row fails — the app crashes at every launch, with no way to reach the event. 5 000 is
         * some 70 kB of text, far above any real series (a daily one cancelled every day for 13 years).
         *
         * Enforced by the import's reader, which refuses the rule whole rather than cutting the list (a
         * cancellation dropped is an occurrence brought back), and deliberately NOT in [init]: a row or a
         * backup of the user's own must never become unreadable because of it.
         */
        const val MAX_EXDATES = 5_000
    }
}

/**
 * The day of the month a monthly series falls on — RFC 5545 `BYDAY` with an ordinal, or
 * `BYMONTHDAY=-1`, under `FREQ=MONTHLY`.
 *
 * The weekday and the rank are stored, not derived from the series' start on every read: the expander,
 * the `.ics` export and the backup each need them, and none of them should have to know in which zone
 * the start was counted. The editor derives them from the start when it saves (see
 * `ui/screens/editor/MonthlyMode`), so the two cannot drift apart through it.
 */
sealed interface MonthlyRule {

    /** The start's day number: "on day 10". A month without that day (the 31st in April) is skipped. */
    data object SameDay : MonthlyRule

    /**
     * The [ordinal]-th [weekday] of the month — 1 to 5, or [LAST] for the last one: "on the second
     * Tuesday", "on the last Friday". A month without a fifth one is skipped, as RFC 5545 says.
     */
    data class NthWeekday(val ordinal: Int, val weekday: Weekday) : MonthlyRule {
        init {
            require(ordinal in 1..MAX_ORDINAL || ordinal == LAST) {
                "a monthly position is 1..$MAX_ORDINAL or $LAST, was $ordinal"
            }
        }
    }

    /** The month's last day, whatever its length. */
    data object LastDay : MonthlyRule

    /**
     * The two plain numbers the database and the backup keep: [storedOrdinal] and [storedWeekdayIso].
     *
     * | rule              | ordinal | weekday (ISO) |
     * |-------------------|---------|---------------|
     * | [SameDay]         | null    | null          |
     * | [NthWeekday]      | 1..5/-1 | 1..7          |
     * | [LastDay]         | -1      | null          |
     *
     * Modelled on RFC 5545 itself — `BYDAY=-1FR` is "the -1st Friday", `BYMONTHDAY=-1` "the -1st day" —
     * and chosen so that a row written before the columns existed (both null) reads as [SameDay], which
     * is what every monthly series was until then. Append-only: never reuse a combination.
     */
    val storedOrdinal: Int?
        get() = when (this) {
            SameDay -> null
            is NthWeekday -> ordinal
            LastDay -> LAST
        }

    /** See [storedOrdinal]. */
    val storedWeekdayIso: Int?
        get() = (this as? NthWeekday)?.weekday?.isoValue

    companion object {
        /** The ordinal RFC 5545 gives to "the last one". */
        const val LAST = -1

        /** No month holds a sixth weekday of any kind. */
        const val MAX_ORDINAL = 5

        /**
         * The rule [storedOrdinal] and [storedWeekdayIso] describe, for a series of frequency [freq].
         *
         * Never throws: both readers (the database, an `.atbak`) run on data this build did not
         * necessarily write, and an exception there would make the whole agenda unreadable — the
         * scenario `RecurrenceRule.MAX_INTERVAL` describes. A combination this app never writes (a
         * hand-edited backup, a row from a future build) reads as [SameDay], the series' day number,
         * which is also what it read as before this field existed.
         */
        fun fromStored(freq: RecurrenceFreq, ordinal: Int?, weekdayIso: Int?): MonthlyRule {
            if (freq != RecurrenceFreq.MONTHLY || ordinal == null) return SameDay
            val rankOk = ordinal in 1..MAX_ORDINAL || ordinal == LAST
            return when {
                weekdayIso == null && ordinal == LAST -> LastDay
                weekdayIso != null && weekdayIso in 1..Weekday.entries.size && rankOk ->
                    NthWeekday(ordinal, Weekday.fromIso(weekdayIso))
                else -> SameDay.also {
                    Timber.w("Unknown monthly position (%d, %s) — read as the same day", ordinal, weekdayIso)
                }
            }
        }
    }
}
