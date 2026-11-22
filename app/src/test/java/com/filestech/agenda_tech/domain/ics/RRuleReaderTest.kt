package com.filestech.agenda_tech.domain.ics

import com.filestech.agenda_tech.domain.device.DeviceEventMapper
import com.filestech.agenda_tech.domain.model.DeviceEvent
import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.RecurrenceFreq
import com.filestech.agenda_tech.domain.model.RecurrenceRule
import com.filestech.agenda_tech.domain.model.Weekday
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What each import makes of an `RRULE`. Every rule goes through BOTH — a `.ics` file and a device-calendar
 * row — and the two must agree: they had a parser each, which had already drifted (`2TU` dropped by one,
 * read as `TU` by the other), and both turned rules they did not understand into series on other dates.
 */
class RRuleReaderTest {

    private val paris = ZoneId.of("Europe/Paris")

    /** 10 November 2026, a Tuesday — the second of its month. */
    private val secondTuesday = LocalDateTime.of(2026, 11, 10, 9, 0)

    /** What an import made of a rule: the series, or the first date only. */
    private data class Taken(val rule: RecurrenceRule?, val firstDateOnly: Boolean)

    /** [exDates]: UTC stamps (`…Z`), one EXDATE line in a file, the EXDATE column on a device row. */
    private fun viaFile(rrule: String?, start: LocalDateTime, exDates: String? = null): Taken {
        val text = buildString {
            append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:x@example.com\r\n")
            append("DTSTART;TZID=Europe/Paris:${start.format(STAMP)}\r\n")
            append("DTEND;TZID=Europe/Paris:${start.plusHours(1).format(STAMP)}\r\n")
            append("SUMMARY:Club\r\n")
            rrule?.let { append("RRULE:$it\r\n") }
            exDates?.let { append("EXDATE:$it\r\n") }
            append("END:VEVENT\r\nEND:VCALENDAR\r\n")
        }
        val event = IcsCodec.decode(text, paris).single()
        return Taken(event.recurrence, event.firstDateOnly)
    }

    private fun viaDevice(rrule: String?, start: LocalDateTime, exDates: String? = null): Taken {
        val row = DeviceEvent(
            uid = "x",
            title = "Club",
            description = null,
            location = null,
            dtStartUtcMillis = start.atZone(paris).toInstant().toEpochMilli(),
            // A recurring provider row carries a DURATION instead of an end, as the platform contract says.
            dtEndUtcMillis = null,
            durationRfc = "PT1H",
            eventTimeZone = paris.id,
            allDay = false,
            rrule = rrule,
            exDate = exDates,
            deviceId = 1,
            originalId = null,
            originalInstanceTime = null,
        )
        val mapped = DeviceEventMapper.map(row, calendarId = 1, defaultZone = paris)!!
        return Taken(mapped.event.recurrence, mapped.firstDateOnly)
    }

    /** Both imports, asserted to agree. */
    private fun taken(rrule: String?, start: LocalDateTime = secondTuesday, exDates: String? = null): Taken {
        val file = viaFile(rrule, start, exDates)
        assertThat(viaDevice(rrule, start, exDates)).isEqualTo(file)
        return file
    }

    private fun reproduced(rule: RecurrenceRule) = Taken(rule, firstDateOnly = false)

    private fun monthly(rule: MonthlyRule) = RecurrenceRule(RecurrenceFreq.MONTHLY, monthly = rule)

    // --- Monthly by position ------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = ["FREQ=MONTHLY;BYDAY=2TU", "FREQ=MONTHLY;BYDAY=+2TU", "FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2", "freq=monthly;byday=2tu"],
    )
    fun `the second Tuesday is read as the second Tuesday, in all its spellings`(rrule: String) {
        assertThat(taken(rrule)).isEqualTo(reproduced(monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY))))
    }

    @ParameterizedTest
    @ValueSource(strings = ["FREQ=MONTHLY;BYDAY=-1FR", "FREQ=MONTHLY;BYDAY=FR;BYSETPOS=-1"])
    fun `the last Friday is read as the last Friday`(rrule: String) {
        val lastFriday = LocalDateTime.of(2026, 11, 27, 18, 0)
        assertThat(taken(rrule, lastFriday))
            .isEqualTo(reproduced(monthly(MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.FRIDAY))))
    }

    @Test
    fun `a fifth weekday and the last day are read as such`() {
        val lastDay = LocalDateTime.of(2026, 11, 30, 8, 0) // a Monday, the fifth of the month
        assertThat(taken("FREQ=MONTHLY;BYDAY=5MO", lastDay)).isEqualTo(reproduced(monthly(MonthlyRule.NthWeekday(5, Weekday.MONDAY))))
        assertThat(taken("FREQ=MONTHLY;BYMONTHDAY=-1", lastDay)).isEqualTo(reproduced(monthly(MonthlyRule.LastDay)))
    }

    @Test
    fun `BYMONTHDAY naming the start's own day is the plain monthly rule`() {
        assertThat(taken("FREQ=MONTHLY;BYMONTHDAY=10")).isEqualTo(reproduced(monthly(MonthlyRule.SameDay)))
        assertThat(taken("FREQ=MONTHLY")).isEqualTo(reproduced(monthly(MonthlyRule.SameDay)))
    }

    @Test
    fun `the bounds and the interval are kept beside the position`() {
        val until = LocalDateTime.of(2027, 12, 31, 22, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val secondTuesdays = MonthlyRule.NthWeekday(2, Weekday.TUESDAY)
        assertThat(taken("FREQ=MONTHLY;INTERVAL=2;BYDAY=2TU;COUNT=5")).isEqualTo(
            reproduced(RecurrenceRule(RecurrenceFreq.MONTHLY, interval = 2, count = 5, monthly = secondTuesdays)),
        )
        assertThat(taken("FREQ=MONTHLY;BYDAY=2TU;UNTIL=20271231T225959Z")).isEqualTo(
            reproduced(RecurrenceRule(RecurrenceFreq.MONTHLY, untilUtcMillis = until, monthly = secondTuesdays)),
        )
    }

    // --- Other shapes that change nothing, or that are the same series said differently -------------

    @Test
    fun `every weekday, said with DAILY, is the weekly rule on those days`() {
        val monday = LocalDateTime.of(2026, 11, 16, 9, 0)
        val weekdays = setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY)
        assertThat(taken("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR", monday))
            .isEqualTo(reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, byWeekdays = weekdays)))
    }

    @Test
    fun `a yearly rule that repeats its start's month and day, as Outlook writes it, is the yearly rule`() {
        val yearly = reproduced(RecurrenceRule(RecurrenceFreq.YEARLY))
        assertThat(taken("FREQ=YEARLY;BYMONTH=11")).isEqualTo(yearly)
        assertThat(taken("FREQ=YEARLY;BYMONTHDAY=10;BYMONTH=11")).isEqualTo(yearly)
    }

    @Test
    fun `WKST is ignored where it moves nothing`() {
        // Every other week on Tuesday and Thursday groups the same way whether weeks begin on Monday or on
        // Sunday — the shape calendars actually write with WKST=SU.
        assertThat(taken("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;WKST=SU")).isEqualTo(
            reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, interval = 2, byWeekdays = setOf(Weekday.TUESDAY, Weekday.THURSDAY))),
        )
        assertThat(taken("FREQ=WEEKLY;BYDAY=TU,SU;WKST=SU")).isEqualTo(
            reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, byWeekdays = setOf(Weekday.TUESDAY, Weekday.SUNDAY))),
        )
    }

    @Test
    fun `a bound read exactly is kept, the largest interval and a good UNTIL beside a COUNT of 0 included`() {
        val until = LocalDateTime.of(2027, 12, 31, 22, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

        assertThat(taken("FREQ=WEEKLY;INTERVAL=999")).isEqualTo(reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, interval = 999)))
        // COUNT=0 is no bound, and must not take a perfectly good UNTIL down with it.
        assertThat(taken("FREQ=WEEKLY;COUNT=0;UNTIL=20271231T225959Z"))
            .isEqualTo(reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, untilUtcMillis = until)))
        // A valid COUNT decides the end; the UNTIL beside it is not read, readable or not.
        assertThat(taken("FREQ=WEEKLY;COUNT=3;UNTIL=nonsense")).isEqualTo(reproduced(RecurrenceRule(RecurrenceFreq.WEEKLY, count = 3)))
    }

    @Test
    fun `a yearly rule with BYMONTHDAY is the start's day once a year only beside its own month`() {
        // RFC 5545 §3.3.10 expands BYMONTHDAY within the year under YEARLY: "FREQ=YEARLY;BYMONTHDAY=15"
        // from 15 January is the 15th of every month. With BYMONTH=1 it is 15 January, once a year — the
        // form Outlook writes.
        val january15 = LocalDateTime.of(2027, 1, 15, 9, 0)

        assertThat(taken("FREQ=YEARLY;BYMONTHDAY=15", january15)).isEqualTo(Taken(rule = null, firstDateOnly = true))
        assertThat(taken("FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=15", january15))
            .isEqualTo(reproduced(RecurrenceRule(RecurrenceFreq.YEARLY)))
        assertThat(taken("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=15", january15)).isEqualTo(Taken(rule = null, firstDateOnly = true))
    }

    // --- Cancelled occurrences: one cell of text, which a crafted file must not make unreadable ----------

    /** [count] distinct cancelled instants, as UTC stamps one minute apart. */
    private fun exDateStamps(count: Int): String {
        val first = LocalDateTime.of(2027, 1, 1, 0, 0)
        return (0 until count).joinToString(",") { "${first.plusMinutes(it.toLong()).format(STAMP)}Z" }
    }

    @Test
    fun `a rule with more cancelled occurrences than any real series is taken as its first date`() {
        // 100 000 distinct EXDATEs fit in a 5 MiB file. Stored, they make one TEXT cell of 1.4 MB — and at
        // 300 000, past 2 MB, every query reading the row fails: the app crashes at every launch. Cutting
        // the list down would bring back the occurrences cut, so the rule is refused whole.
        assertThat(taken("FREQ=DAILY", exDates = exDateStamps(100_000))).isEqualTo(Taken(rule = null, firstDateOnly = true))
    }

    @Test
    fun `up to the cap, every cancelled occurrence is kept, duplicates counted once`() {
        val atCap = exDateStamps(RecurrenceRule.MAX_EXDATES)
        val withDuplicates = "$atCap,${exDateStamps(10)}" // ten more stamps, all already in the list

        val kept = taken("FREQ=DAILY", exDates = withDuplicates).rule
        assertThat(kept?.exDatesUtcMillis).hasSize(RecurrenceRule.MAX_EXDATES)
    }

    @Test
    fun `an event that does not repeat is neither a series nor counted`() {
        assertThat(taken(null)).isEqualTo(Taken(rule = null, firstDateOnly = false))
    }

    // --- Rules this app cannot follow: the first date, and said ---------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = [
            "FREQ=MONTHLY;BYDAY=TU", // every Tuesday of the month
            "FREQ=MONTHLY;BYDAY=2TU,4TU", // two positions
            "FREQ=MONTHLY;BYDAY=-2TU", // the second to last
            "FREQ=MONTHLY;BYDAY=3TU", // a position its own start is not on
            "FREQ=MONTHLY;BYMONTHDAY=1,15",
            "FREQ=MONTHLY;BYMONTHDAY=15", // another day than the start's
            "FREQ=MONTHLY;BYMONTHDAY=-1", // the last day, from a start that is not one
            "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", // the last working day
            "FREQ=MONTHLY;BYDAY=TU;BYSETPOS=2,4",
            "FREQ=MONTHLY;BYDAY=2TU;BYMONTHDAY=10",
            "FREQ=MONTHLY;BYSETPOS=2",
            "FREQ=MONTHLY;BYMONTH=11",
            "FREQ=DAILY;INTERVAL=2;BYDAY=MO,TU", // every other day, on those days only
            "FREQ=DAILY;BYDAY=2TU",
            "FREQ=DAILY;BYMONTHDAY=10",
            "FREQ=WEEKLY;BYDAY=2TU",
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,SU;WKST=SU", // the case WKST does move (RFC 5545 §3.3.10)
            "FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10",
            "FREQ=YEARLY;BYMONTH=3",
            "FREQ=YEARLY;BYMONTHDAY=11",
            "FREQ=YEARLY;BYWEEKNO=20",
            "FREQ=YEARLY;BYYEARDAY=100",
            "FREQ=DAILY;BYHOUR=9,17",
            "FREQ=MONTHLY;RSCALE=CHINESE",
            "FREQ=HOURLY",
            "FREQ=DAILY;FREQ=WEEKLY", // a part given twice
            "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=WEEKLY;BYDAY=",
            "FREQ=WEEKLY;WKST=XX",
            "BYDAY=TU", // no FREQ at all
            "FREQ=YEARLY;BYMONTHDAY=10", // RFC 5545: the 10th of EVERY month, not once a year
            // Bounds this reader cannot read exactly: a series on other dates, or one that never ends.
            "FREQ=WEEKLY;INTERVAL=1000", // was clamped to 999
            "FREQ=WEEKLY;INTERVAL=0", // was raised to 1
            "FREQ=WEEKLY;INTERVAL=-2",
            "FREQ=WEEKLY;INTERVAL=abc",
            "FREQ=WEEKLY;COUNT=0", // RFC 5545: the start alone — was read as an endless series
            "FREQ=WEEKLY;COUNT=-3",
            "FREQ=WEEKLY;COUNT=abc",
            "FREQ=WEEKLY;UNTIL=20271331T000000Z", // unreadable: was read as an endless series
            "FREQ=WEEKLY;COUNT=0;UNTIL=nonsense",
        ],
    )
    fun `a rule this app cannot reproduce is taken as its first date, and counted`(rrule: String) {
        assertThat(taken(rrule)).isEqualTo(Taken(rule = null, firstDateOnly = true))
    }

    // --- The export writes back what the import reads ------------------------------------------------

    @Test
    fun `the positions are written the way RFC 5545 says, and read back the same`() {
        fun exported(rule: MonthlyRule, start: LocalDateTime): Pair<String, IcsEvent> {
            val event = IcsEvent(
                title = "Club",
                description = null,
                location = null,
                startUtcMillis = start.atZone(paris).toInstant().toEpochMilli(),
                endUtcMillis = start.plusHours(1).atZone(paris).toInstant().toEpochMilli(),
                timeZoneId = paris.id,
                allDay = false,
                recurrence = monthly(rule),
            )
            val text = IcsCodec.encode(listOf(event), nowUtcMillis = 0)
            return text to event
        }

        val lastFriday = LocalDateTime.of(2026, 11, 27, 18, 0)
        val lastDay = LocalDateTime.of(2026, 11, 30, 8, 0)
        listOf(
            Triple(MonthlyRule.NthWeekday(2, Weekday.TUESDAY), secondTuesday, "RRULE:FREQ=MONTHLY;BYDAY=2TU"),
            Triple(MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.FRIDAY), lastFriday, "RRULE:FREQ=MONTHLY;BYDAY=-1FR"),
            Triple(MonthlyRule.LastDay, lastDay, "RRULE:FREQ=MONTHLY;BYMONTHDAY=-1"),
            Triple(MonthlyRule.SameDay, secondTuesday, "RRULE:FREQ=MONTHLY\r\n"),
        ).forEach { (rule, start, line) ->
            val (text, written) = exported(rule, start)
            assertThat(text).contains(line)
            assertThat(IcsCodec.decode(text, paris).single().recurrence).isEqualTo(written.recurrence)
        }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}
