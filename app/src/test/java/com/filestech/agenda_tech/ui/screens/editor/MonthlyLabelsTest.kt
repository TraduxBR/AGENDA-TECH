package com.filestech.agenda_tech.ui.screens.editor

import com.filestech.agenda_tech.R
import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.Weekday
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The table from a rank and a weekday to its phrase is 42 hand-written cells: one swapped cell would have
 * the editor offer "the second Wednesday" for a series that falls on Tuesdays. Each cell is checked here
 * against the resource NAMED after it, so the table and the names cannot disagree.
 */
class MonthlyLabelsTest {

    @Test
    fun `each rank and weekday reads its own phrase`() {
        val ranks = listOf(1 to "first", 2 to "second", 3 to "third", 4 to "fourth", 5 to "fifth", MonthlyRule.LAST to "last")
        val seen = mutableSetOf<Int>()

        ranks.forEach { (rank, word) ->
            Weekday.entries.forEach { weekday ->
                val named = R.string::class.java.getField("recurrence_monthly_${word}_${weekday.name.lowercase()}").getInt(null)
                val shown = nthWeekdayLabelRes(MonthlyRule.NthWeekday(rank, weekday))
                assertThat(shown).isEqualTo(named)
                seen += shown
            }
        }

        assertThat(seen).hasSize(ranks.size * Weekday.entries.size)
    }
}
