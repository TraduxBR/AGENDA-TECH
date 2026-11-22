package com.filestech.agenda_tech.domain.recurrence

import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.Weekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The date [this] rule picks in [month], for a series whose start falls on day [startDayOfMonth] of its
 * own month; null when [month] has no such day — the 31st in April, a fifth Tuesday in a month with four.
 * Such a month is skipped, never clamped onto a neighbour: RFC 5545 ignores an invalid date, and the
 * expander has always skipped the 31st the same way.
 */
fun MonthlyRule.dateIn(month: YearMonth, startDayOfMonth: Int): LocalDate? = when (this) {
    MonthlyRule.SameDay -> if (startDayOfMonth <= month.lengthOfMonth()) month.atDay(startDayOfMonth) else null
    MonthlyRule.LastDay -> month.atEndOfMonth()
    is MonthlyRule.NthWeekday -> {
        val day = DayOfWeek.of(weekday.isoValue)
        if (ordinal == MonthlyRule.LAST) {
            month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(day))
        } else {
            // `dayOfWeekInMonth` runs on into the next month when this one has no fifth such day.
            month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(ordinal, day))
                .takeIf { YearMonth.from(it) == month }
        }
    }
}

/**
 * Every monthly rule that has [start] among its dates, in the order the editor offers them: the day
 * number, the weekday by its rank ("the second Tuesday"), the last such weekday when [start] is in the
 * month's last seven days, and the last day when [start] is the last day.
 *
 * One answer to two questions, kept in one place so they cannot disagree: what the editor may offer for
 * a series beginning on [start], and whether an imported rule really passes through its own start.
 */
fun monthlyRulesThrough(start: LocalDate): List<MonthlyRule> {
    val weekday = Weekday.fromIso(start.dayOfWeek.value)
    val length = start.lengthOfMonth()
    return buildList {
        add(MonthlyRule.SameDay)
        add(MonthlyRule.NthWeekday((start.dayOfMonth - 1) / DAYS_PER_WEEK + 1, weekday))
        if (start.dayOfMonth > length - DAYS_PER_WEEK) add(MonthlyRule.NthWeekday(MonthlyRule.LAST, weekday))
        if (start.dayOfMonth == length) add(MonthlyRule.LastDay)
    }
}

private const val DAYS_PER_WEEK = 7
