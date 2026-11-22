package com.filestech.agenda_tech.ui.screens.editor

import com.filestech.agenda_tech.core.time.FarZones
import com.filestech.agenda_tech.domain.model.MonthlyRule
import com.filestech.agenda_tech.domain.model.RecurrenceFreq
import com.filestech.agenda_tech.domain.model.RecurrenceRule
import com.filestech.agenda_tech.domain.model.Weekday
import com.filestech.agenda_tech.domain.recurrence.monthlyRulesThrough
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A monthly series by position in the editor: the choice is a kind ("by its rank", "the last one"), and
 * the weekday and rank are worked out from the start each time — so moving the start never leaves a rule
 * that misses it, and opening and saving an event never rewrites the rule it came with.
 */
internal class EventEditorMonthlyTest : EventEditorTestBase() {

    private fun monthly(position: MonthlyRule) = RecurrenceRule(RecurrenceFreq.MONTHLY, monthly = position)

    /** A monthly series starting on [date] at 09:00 on the phone's clock, saved as row 10. */
    private fun seedSeries(date: LocalDate, position: MonthlyRule) {
        val start = at(date.year, date.monthValue, date.dayOfMonth, 9)
        eventRepo.rows[10] = seedEvent(recurrence = monthly(position))
            .copy(startUtcMillis = start, endUtcMillis = start + HOUR)
    }

    private fun EventEditorViewModel.newMonthlyEvent(date: LocalDate, mode: MonthlyMode) {
        onTitleChange("Club")
        onStartDateChange(date)
        onRecurrenceSelect(RecurrenceFreq.MONTHLY)
        onMonthlyModeChange(mode)
    }

    private fun savedRule() = eventRepo.rows.values.single { it.recurrenceParentId == null }.recurrence

    @Test
    fun `the second Tuesday is saved as the second Tuesday, worked out from the start`() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.newMonthlyEvent(LocalDate.of(2026, 11, 10), MonthlyMode.NTH_WEEKDAY)

        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(savedRule()).isEqualTo(monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY)))
    }

    @Test
    fun `moving the start keeps the kind of choice, never a rule that misses the new start`() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.newMonthlyEvent(LocalDate.of(2026, 11, 10), MonthlyMode.NTH_WEEKDAY)

        vm.onStartDateChange(LocalDate.of(2026, 11, 20)) // the third Friday
        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(savedRule()).isEqualTo(monthly(MonthlyRule.NthWeekday(3, Weekday.FRIDAY)))
    }

    @Test
    fun `a choice the new start does not offer falls back on its nearest kin, on screen and when saved`() =
        runTest(dispatcher) {
            val vm = viewModel()
            testScheduler.advanceUntilIdle()
            vm.newMonthlyEvent(LocalDate.of(2026, 11, 30), MonthlyMode.LAST_DAY)

            vm.onStartDateChange(LocalDate.of(2026, 11, 15))
            val anchor = vm.savedStart(vm.state.value).toLocalDate()
            assertThat(vm.state.value.recurrenceMonthlyMode.selectedOn(anchor)).isEqualTo(MonthlyMode.DAY_OF_MONTH)
            vm.onSave()
            testScheduler.advanceUntilIdle()

            assertThat(savedRule()).isEqualTo(monthly(MonthlyRule.SameDay))
        }

    @Test
    fun `the last Friday moved out of the last week becomes that Friday's rank`() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.newMonthlyEvent(LocalDate.of(2026, 11, 27), MonthlyMode.LAST_WEEKDAY)

        vm.onStartDateChange(LocalDate.of(2026, 11, 13)) // the second Friday
        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(savedRule()).isEqualTo(monthly(MonthlyRule.NthWeekday(2, Weekday.FRIDAY)))
    }

    @Test
    fun `an imported fifth Tuesday shows as the fifth and is saved unchanged`() = runTest(dispatcher) {
        val fifthTuesday = MonthlyRule.NthWeekday(5, Weekday.TUESDAY)
        seedSeries(LocalDate.of(2026, 12, 29), fifthTuesday)
        val vm = viewModel(eventId = 10)
        testScheduler.advanceUntilIdle()

        val anchor = vm.savedStart(vm.state.value).toLocalDate()
        assertThat(vm.state.value.recurrenceMonthlyMode).isEqualTo(MonthlyMode.NTH_WEEKDAY)
        assertThat(vm.state.value.recurrenceMonthlyMode.ruleOn(anchor)).isEqualTo(fifthTuesday)
        vm.onAddReminder(15)
        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(eventRepo.rows.getValue(10).recurrence).isEqualTo(monthly(fifthTuesday))
    }

    @Test
    fun `the last Friday is loaded as the last one, not as the fourth it also is`() = runTest(dispatcher) {
        val lastFriday = MonthlyRule.NthWeekday(MonthlyRule.LAST, Weekday.FRIDAY)
        seedSeries(LocalDate.of(2026, 11, 27), lastFriday)
        val vm = viewModel(eventId = 10)
        testScheduler.advanceUntilIdle()

        assertThat(vm.state.value.recurrenceMonthlyMode).isEqualTo(MonthlyMode.LAST_WEEKDAY)
        vm.onTitleChange("Club — renommé")
        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(eventRepo.rows.getValue(10).recurrence).isEqualTo(monthly(lastFriday))
    }

    @Test
    fun `leaving monthly for weekly saves a weekly rule, whatever was chosen for the month`() = runTest(dispatcher) {
        // RecurrenceRule refuses a position on any other frequency: carried over, it would crash the save.
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        vm.newMonthlyEvent(LocalDate.of(2026, 11, 10), MonthlyMode.NTH_WEEKDAY)

        vm.onRecurrenceSelect(RecurrenceFreq.WEEKLY)
        vm.onSave()
        testScheduler.advanceUntilIdle()

        assertThat(savedRule()?.freq).isEqualTo(RecurrenceFreq.WEEKLY)
        assertThat(savedRule()?.monthly).isEqualTo(MonthlyRule.SameDay)
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `a duplicate of a series by position keeps its position`() = runTest(dispatcher) {
        seedSeries(LocalDate.of(2026, 11, 10), MonthlyRule.NthWeekday(2, Weekday.TUESDAY))
        val vm = viewModel(eventId = 10)
        testScheduler.advanceUntilIdle()

        vm.onDuplicate("Club (copie)")
        vm.onSave()
        testScheduler.advanceUntilIdle()

        val copy = eventRepo.rows.values.single { it.id != 10L }
        assertThat(copy.recurrence).isEqualTo(monthly(MonthlyRule.NthWeekday(2, Weekday.TUESDAY)))
    }

    @Test
    fun `editing one occurrence leaves the series' position as it was`() = runTest(dispatcher) {
        seedSeries(LocalDate.of(2026, 11, 10), MonthlyRule.NthWeekday(2, Weekday.TUESDAY))
        val december8 = at(2026, 12, 8, 9)
        val vm = viewModel(eventId = 10, occurrenceStart = december8)
        testScheduler.advanceUntilIdle()

        vm.onTitleChange("Club — exceptionnel")
        vm.onSave()
        testScheduler.advanceUntilIdle()
        vm.confirmScope(applyToSeries = false)
        testScheduler.advanceUntilIdle()

        val master = eventRepo.rows.getValue(10)
        assertThat(master.recurrence?.monthly).isEqualTo(MonthlyRule.NthWeekday(2, Weekday.TUESDAY))
        assertThat(master.recurrence?.exDatesUtcMillis).containsExactly(december8)
        assertThat(eventRepo.rows.values.single { it.recurrenceParentId == 10L }.recurrence).isNull()
    }

    @Test
    fun `an event authored in another zone keeps the rank counted in that zone`() = runTest(dispatcher) {
        // 00:30 UTC: the 10th in a zone fourteen hours ahead, the 9th in one eleven hours behind. Whatever
        // the machine's zone, one of the two is on another date than the phone's clock — the case where
        // counting from the date the form shows would save a rule that misses the series' own start.
        val instant = Instant.parse("2026-11-10T00:30:00Z")
        val phoneDate = instant.atZone(zone).toLocalDate()
        val authored = listOf(FarZones.AHEAD, FarZones.BEHIND).map(ZoneId::of)
            .first { instant.atZone(it).toLocalDate() != phoneDate }
        val authoredDate = instant.atZone(authored).toLocalDate()
        val position = monthlyRulesThrough(authoredDate)[1] as MonthlyRule.NthWeekday
        eventRepo.rows[10] = seedEvent(recurrence = monthly(position)).copy(
            startUtcMillis = instant.toEpochMilli(),
            endUtcMillis = instant.toEpochMilli() + HOUR,
            timeZoneId = authored.id,
        )
        val vm = viewModel(eventId = 10)
        testScheduler.advanceUntilIdle()

        assertThat(vm.savedStart(vm.state.value).toLocalDate()).isEqualTo(authoredDate)
        vm.onAddReminder(15)
        vm.onSave()
        testScheduler.advanceUntilIdle()

        val saved = eventRepo.rows.getValue(10)
        assertThat(saved.timeZoneId).isEqualTo(authored.id)
        assertThat(saved.recurrence?.monthly).isEqualTo(position)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
