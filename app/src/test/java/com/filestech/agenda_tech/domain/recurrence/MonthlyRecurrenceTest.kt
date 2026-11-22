package com.filestech.agenda_tech.domain.recurrence

import com.filestech.agenda_tech.domain.model.Event
import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.RecurrenceFreq
import com.filestech.agenda_tech.domain.model.RecurrenceRule
import com.filestech.agenda_tech.domain.model.Weekday
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Monthly series by position — "the second Tuesday", "the last Friday", "the last day" — as the expander
 * produces them. Every view, the widget and the reminders take their dates from here, so this is where a
 * wrong date would start.
 */
class MonthlyRecurrenceTest {

    private val expander = RecurrenceExpander()
    private val paris = ZoneId.of("Europe/Paris")

    private fun ms(t: LocalDateTime): Long = t.atZone(paris).toInstant().toEpochMilli()

    private fun series(start: LocalDateTime, rule: RecurrenceRule) = Event(
        id = 1,
        calendarId = 1,
        title = "Club",
        startUtcMillis = ms(start),
        endUtcMillis = ms(start.plusHours(1)),
        timeZoneId = paris.id,
        recurrence = rule,
    )

    private fun monthly(monthly: MonthlyRule, interval: Int = 1, count: Int? = null, until: Long? = null) =
        RecurrenceRule(RecurrenceFreq.MONTHLY, interval = interval, count = count, untilUtcMillis = until, monthly = monthly)

    private fun startsBetween(event: Event, from: LocalDate, to: LocalDate): List<LocalDateTime> =
        expander.expand(event, ms(from.atStartOfDay()), ms(to.atStartOfDay()))
            .map { java.time.Instant.ofEpochMilli(it.startUtcMillis).atZone(paris).toLocalDateTime() }

    // --- Which day of each month ------------------------------------------------------------------

    @Test
    fun `the second Tuesday falls on the second Tuesday of every month, not on the start's day number`() {
        // The defect this feature closes: 10 Nov 2026 is the second Tuesday; the 10th of December is a
        // Thursday, and the second Tuesday of December is the 8th.
        val event = series(LocalDateTime.of(2026, 11, 10, 9, 0), monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY)))

        assertThat(startsBetween(event, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 1))).containsExactly(
            LocalDateTime.of(2026, 11, 10, 9, 0),
            LocalDateTime.of(2026, 12, 8, 9, 0),
            LocalDateTime.of(2027, 1, 12, 9, 0),
            LocalDateTime.of(2027, 2, 9, 9, 0),
        ).inOrder()
    }

    @Test
    fun `the last Friday is the last one, whether the month has four or five`() {
        val event = series(LocalDateTime.of(2026, 11, 27, 18, 0), monthly(MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.FRIDAY)))

        assertThat(startsBetween(event, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 1))).containsExactly(
            LocalDateTime.of(2026, 11, 27, 18, 0),
            LocalDateTime.of(2026, 12, 25, 18, 0),
            LocalDateTime.of(2027, 1, 29, 18, 0),
            LocalDateTime.of(2027, 2, 26, 18, 0),
        ).inOrder()
    }

    @Test
    fun `a fifth Tuesday skips the months that have only four, as RFC 5545 says`() {
        val event = series(LocalDateTime.of(2026, 12, 29, 9, 0), monthly(MonthlyRule.NthWeekday(5, Weekday.TUESDAY)))

        assertThat(startsBetween(event, LocalDate.of(2026, 12, 1), LocalDate.of(2028, 1, 1))).containsExactly(
            LocalDateTime.of(2026, 12, 29, 9, 0),
            LocalDateTime.of(2027, 3, 30, 9, 0),
            LocalDateTime.of(2027, 6, 29, 9, 0),
            LocalDateTime.of(2027, 8, 31, 9, 0),
            LocalDateTime.of(2027, 11, 30, 9, 0),
        ).inOrder()
    }

    @Test
    fun `the last day follows the length of each month`() {
        val event = series(LocalDateTime.of(2027, 1, 31, 8, 0), monthly(MonthlyRule.LastDay))

        assertThat(startsBetween(event, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 5, 1))).containsExactly(
            LocalDateTime.of(2027, 1, 31, 8, 0),
            LocalDateTime.of(2027, 2, 28, 8, 0),
            LocalDateTime.of(2027, 3, 31, 8, 0),
            LocalDateTime.of(2027, 4, 30, 8, 0),
        ).inOrder()
    }

    @Test
    fun `the day number still skips the months without that day`() {
        // The plain rule went through the same new branch; its behaviour must not have moved.
        val event = series(LocalDateTime.of(2025, 1, 31, 9, 0), monthly(MonthlyRule.SameDay))

        assertThat(startsBetween(event, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 5, 1))).containsExactly(
            LocalDateTime.of(2025, 1, 31, 9, 0),
            LocalDateTime.of(2025, 3, 31, 9, 0),
        ).inOrder()
    }

    // --- INTERVAL, COUNT, UNTIL, EXDATE, like every other branch ----------------------------------

    @Test
    fun `an interval skips whole months`() {
        val event = series(LocalDateTime.of(2026, 11, 10, 9, 0), monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY), interval = 2))

        assertThat(startsBetween(event, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 4, 1))).containsExactly(
            LocalDateTime.of(2026, 11, 10, 9, 0),
            LocalDateTime.of(2027, 1, 12, 9, 0),
            LocalDateTime.of(2027, 3, 9, 9, 0),
        ).inOrder()
    }

    @Test
    fun `COUNT counts the occurrences produced, not the months skipped`() {
        val event = series(LocalDateTime.of(2026, 12, 29, 9, 0), monthly(MonthlyRule.NthWeekday(5, Weekday.TUESDAY), count = 3))

        assertThat(startsBetween(event, LocalDate.of(2026, 12, 1), LocalDate.of(2029, 1, 1))).containsExactly(
            LocalDateTime.of(2026, 12, 29, 9, 0),
            LocalDateTime.of(2027, 3, 30, 9, 0),
            LocalDateTime.of(2027, 6, 29, 9, 0),
        ).inOrder()
    }

    @Test
    fun `UNTIL is inclusive and an EXDATE removes one occurrence while still counting`() {
        val until = ms(LocalDateTime.of(2027, 2, 9, 9, 0))
        val rule = monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY), until = until)
            .excluding(ms(LocalDateTime.of(2026, 12, 8, 9, 0)))
        val event = series(LocalDateTime.of(2026, 11, 10, 9, 0), rule)

        assertThat(startsBetween(event, LocalDate.of(2026, 11, 1), LocalDate.of(2028, 1, 1))).containsExactly(
            LocalDateTime.of(2026, 11, 10, 9, 0),
            LocalDateTime.of(2027, 1, 12, 9, 0),
            LocalDateTime.of(2027, 2, 9, 9, 0),
        ).inOrder()
    }

    @Test
    fun `the wall-clock hour holds across the change to summer time`() {
        // The second Sunday of March 2027 is the 14th, before the European change (28 March); the
        // second Sunday of April is after it. Both at 10:00 Paris time, one hour apart in UTC.
        val event = series(LocalDateTime.of(2027, 3, 14, 10, 0), monthly(MonthlyRule.NthWeekday(2, Weekday.SUNDAY)))

        val occurrences = expander.expand(event, ms(LocalDateTime.of(2027, 3, 1, 0, 0)), ms(LocalDateTime.of(2027, 5, 1, 0, 0)))

        assertThat(occurrences.map { java.time.Instant.ofEpochMilli(it.startUtcMillis).atZone(paris).toLocalDateTime() })
            .containsExactly(LocalDateTime.of(2027, 3, 14, 10, 0), LocalDateTime.of(2027, 4, 11, 10, 0)).inOrder()
        // Each keeps its hour of length, and the editor's end agrees with the views' end.
        occurrences.forEach { occurrence ->
            assertThat(occurrence.endUtcMillis - occurrence.startUtcMillis).isEqualTo(Duration.ofHours(1).toMillis())
            assertThat(expander.occurrenceEndUtcMillis(event, occurrence.startUtcMillis)).isEqualTo(occurrence.endUtcMillis)
        }
    }

    @Test
    fun `the next occurrence after a date is the next second Tuesday, for the reminders`() {
        val event = series(LocalDateTime.of(2026, 11, 10, 9, 0), monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY)))

        assertThat(expander.nextOccurrenceStart(event, ms(LocalDateTime.of(2026, 11, 11, 0, 0))))
            .isEqualTo(ms(LocalDateTime.of(2026, 12, 8, 9, 0)))
        assertThat(expander.lastOccurrenceStartBefore(event, ms(LocalDateTime.of(2027, 1, 1, 0, 0))))
            .isEqualTo(ms(LocalDateTime.of(2026, 12, 8, 9, 0)))
    }

    // --- A rule that can never fall on a date must not hang every view ------------------------------

    @Test
    fun `a damaged rule with no date at all ends the series instead of looping for ever`() {
        // A fifth Friday in February, every four years from a common year: no such month ever comes. No
        // reader writes this (each checks the rule passes through its start), but a damaged row or a
        // hand-made backup can, and months without a date are not seen by the scan cap or the budget.
        // Without the 400-year bound this call never returns, on the render path of every view.
        val event = series(LocalDateTime.of(2025, 2, 14, 9, 0), monthly(MonthlyRule.NthWeekday(5, Weekday.FRIDAY), interval = 48))

        val next = assertTimeoutPreemptively(
            Duration.ofSeconds(10),
            ThrowingSupplier { expander.nextOccurrenceStart(event, event.startUtcMillis) },
        )

        assertThat(next).isNull()
    }

    // --- The rules a start allows: one answer for the editor and for the import ------------------------

    @Test
    fun `a start in the middle of the month allows its day number and its rank only`() {
        assertThat(monthlyRulesThrough(LocalDate.of(2026, 11, 10))).containsExactly(
            MonthlyRule.SameDay,
            MonthlyRule.NthWeekday(2, Weekday.TUESDAY),
        ).inOrder()
    }

    @Test
    fun `a start in the last seven days also allows the last such weekday`() {
        // 27 Nov 2026 is both the fourth and the last Friday: two different series, both offered.
        assertThat(monthlyRulesThrough(LocalDate.of(2026, 11, 27))).containsExactly(
            MonthlyRule.SameDay,
            MonthlyRule.NthWeekday(4, Weekday.FRIDAY),
            MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.FRIDAY),
        ).inOrder()
    }

    @Test
    fun `the last day of the month allows the last day too, and a fifth weekday is ranked fifth`() {
        assertThat(monthlyRulesThrough(LocalDate.of(2026, 11, 30))).containsExactly(
            MonthlyRule.SameDay,
            MonthlyRule.NthWeekday(5, Weekday.MONDAY),
            MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.MONDAY),
            MonthlyRule.LastDay,
        ).inOrder()
    }

    @Test
    fun `a month without the asked day has no date, never a neighbour's`() {
        assertThat(MonthlyRule.NthWeekday(5, Weekday.TUESDAY).dateIn(YearMonth.of(2027, 2), startDayOfMonth = 29)).isNull()
        assertThat(MonthlyRule.SameDay.dateIn(YearMonth.of(2027, 4), startDayOfMonth = 31)).isNull()
        assertThat(MonthlyRule.LastDay.dateIn(YearMonth.of(2028, 2), startDayOfMonth = 31)).isEqualTo(LocalDate.of(2028, 2, 29))
    }
}
