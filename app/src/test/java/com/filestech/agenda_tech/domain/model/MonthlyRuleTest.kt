package com.filestech.agenda_tech.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The monthly position as a value: its invariants, and the two numbers the database and the backup keep
 * of it — read back without ever throwing, since both readers see data this build did not necessarily
 * write.
 */
class MonthlyRuleTest {

    @Test
    fun `a position in the month is refused on any other frequency`() {
        assertThrows<IllegalArgumentException> {
            RecurrenceRule(RecurrenceFreq.WEEKLY, monthly = MonthlyRule.NthWeekday(2, Weekday.TUESDAY))
        }
        assertThrows<IllegalArgumentException> { RecurrenceRule(RecurrenceFreq.YEARLY, monthly = MonthlyRule.LastDay) }
        // The default is accepted everywhere: it is what every rule was before the field existed.
        assertThat(RecurrenceRule(RecurrenceFreq.DAILY).monthly).isEqualTo(MonthlyRule.SameDay)
    }

    @Test
    fun `a rank is one to five, or the last`() {
        listOf(0, 6, -2).forEach { rank ->
            assertThrows<IllegalArgumentException> { MonthlyRule.NthWeekday(rank, Weekday.MONDAY) }
        }
        assertThat(MonthlyRule.NthWeekday(5, Weekday.MONDAY).ordinal).isEqualTo(5)
        assertThat(MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.MONDAY).ordinal).isEqualTo(-1)
    }

    @Test
    fun `every rule survives the two stored numbers`() {
        val rules = listOf(MonthlyRule.SameDay, MonthlyRule.LastDay) +
            (listOf(1, 2, 3, 4, 5, MonthlyRule.LAST)).flatMap { rank ->
                Weekday.entries.map { MonthlyRule.NthWeekday(rank, it) }
            }

        rules.forEach { rule ->
            assertThat(MonthlyRule.fromStored(RecurrenceFreq.MONTHLY, rule.storedOrdinal, rule.storedWeekdayIso))
                .isEqualTo(rule)
        }
        // And no two of them share a pair: the encoding must tell all 44 apart.
        assertThat(rules.map { it.storedOrdinal to it.storedWeekdayIso }.toSet()).hasSize(rules.size)
    }

    @Test
    fun `a row written before the columns existed reads as the day number`() {
        assertThat(MonthlyRule.fromStored(RecurrenceFreq.MONTHLY, null, null)).isEqualTo(MonthlyRule.SameDay)
    }

    @Test
    fun `a combination this app never writes heals to the day number instead of throwing`() {
        // Each of these would make RecurrenceRule.init throw if passed through, and the reader runs on the
        // render path of every view: one damaged row would make the agenda unreadable (audit F1).
        listOf(
            0 to 2, // rank 0
            6 to 2, // rank 6
            -2 to 5, // second to last: not something this app represents
            2 to null, // a rank with no weekday
            2 to 8, // no such weekday
            -1 to 0, // no such weekday
        ).forEach { (ordinal, weekday) ->
            assertThat(MonthlyRule.fromStored(RecurrenceFreq.MONTHLY, ordinal, weekday)).isEqualTo(MonthlyRule.SameDay)
        }
    }

    @Test
    fun `numbers on a series that is not monthly are inert`() {
        // Not reachable through this app's writes, but a crash if passed through: a weekly rule with a
        // monthly position fails RecurrenceRule's invariant.
        assertThat(MonthlyRule.fromStored(RecurrenceFreq.WEEKLY, 2, 2)).isEqualTo(MonthlyRule.SameDay)
        assertThat(MonthlyRule.fromStored(RecurrenceFreq.YEARLY, -1, null)).isEqualTo(MonthlyRule.SameDay)
    }
}
