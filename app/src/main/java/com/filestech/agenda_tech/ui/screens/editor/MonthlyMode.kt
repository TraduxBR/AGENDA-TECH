package com.filestech.agenda_tech.ui.screens.editor

import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.recurrence.monthlyRulesThrough
import java.time.LocalDate

/**
 * Which day of the month a monthly series falls on, as the editor holds it: a **kind** of choice, not the
 * rule itself. The weekday and its rank are worked out from the series' start when the form is shown and
 * when it is saved ([ruleOn]), so moving the start keeps the choice meaningful — "the second Tuesday"
 * moved to the 20th becomes "the third Friday", as Google Calendar does — instead of leaving a rule that
 * no longer passes through its own start. The weekly days are seeded from the start the same way
 * (`EventEditorViewModel.onRecurrenceSelect`).
 */
enum class MonthlyMode {
    /** The start's day number: "on day 10". */
    DAY_OF_MONTH,

    /** The start's weekday, by its rank in the month: "on the second Tuesday" (up to the fifth). */
    NTH_WEEKDAY,

    /** The start's weekday, the last one of the month — offered when the start is in its last seven days. */
    LAST_WEEKDAY,

    /** The month's last day — offered when the start is the last day. */
    LAST_DAY,
    ;

    /**
     * The rule this choice gives a series starting on [start]. A choice [start] does not offer falls back
     * on its nearest kin — the last weekday on its rank, the last day on the day number — which is also
     * what the screen shows selected, since it asks the same question ([selectedOn]).
     */
    fun ruleOn(start: LocalDate): MonthlyRule {
        val offered = monthlyRulesThrough(start).associateBy(::of)
        return offered[this] ?: offered.getValue(if (this == LAST_DAY) DAY_OF_MONTH else NTH_WEEKDAY)
    }

    /** The choice the screen shows as selected for a series starting on [start] — see [ruleOn]. */
    fun selectedOn(start: LocalDate): MonthlyMode = of(ruleOn(start))

    companion object {
        /** The kind of [rule] — how a loaded series sets the choice. */
        fun of(rule: MonthlyRule): MonthlyMode = when (rule) {
            MonthlyRule.SameDay -> DAY_OF_MONTH
            MonthlyRule.LastDay -> LAST_DAY
            is MonthlyRule.NthWeekday -> if (rule.ordinal == MonthlyRule.LAST) LAST_WEEKDAY else NTH_WEEKDAY
        }
    }
}
