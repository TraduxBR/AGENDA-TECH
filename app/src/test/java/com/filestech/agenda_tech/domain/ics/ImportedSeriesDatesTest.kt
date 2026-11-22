package com.filestech.agenda_tech.domain.ics

import com.filestech.agenda_tech.domain.device.DeviceEventMapper
import com.filestech.agenda_tech.domain.model.DeviceEvent
import com.filestech.agenda_tech.domain.model.Event
import com.filestech.agenda_tech.domain.recurrence.RecurrenceExpander
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The dates an imported series lands on — what the user sees, rather than how the rule is stored. Each
 * case went through both imports onto the wrong dates without a word before they shared one reader:
 * `FREQ=MONTHLY;BYDAY=2TU` recurred on its start's day number (10 Nov → Thursday 10 Dec, not Tuesday
 * 8 Dec), `FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR` on weekends too, and a rule neither understood went on as a
 * plain monthly series.
 */
class ImportedSeriesDatesTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val expander = RecurrenceExpander()

    private fun fromFile(rrule: String, start: LocalDateTime): Event {
        val text = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:x@example.com\r\n" +
            "DTSTART;TZID=Europe/Paris:${start.format(STAMP)}\r\n" +
            "DTEND;TZID=Europe/Paris:${start.plusHours(1).format(STAMP)}\r\n" +
            "SUMMARY:Club\r\nRRULE:$rrule\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        return IcsCodec.decode(text, paris).single().toEvent(calendarId = 1)
    }

    private fun fromDevice(rrule: String, start: LocalDateTime): Event = DeviceEventMapper.map(
        DeviceEvent(
            uid = "x",
            title = "Club",
            description = null,
            location = null,
            dtStartUtcMillis = start.atZone(paris).toInstant().toEpochMilli(),
            dtEndUtcMillis = null,
            durationRfc = "PT1H",
            eventTimeZone = paris.id,
            allDay = false,
            rrule = rrule,
            exDate = null,
            deviceId = 1,
            originalId = null,
            originalInstanceTime = null,
        ),
        calendarId = 1,
        defaultZone = paris,
    )!!.event

    private fun datesBetween(event: Event, from: LocalDate, to: LocalDate): List<LocalDate> =
        expander.expand(
            event,
            from.atStartOfDay(paris).toInstant().toEpochMilli(),
            to.atStartOfDay(paris).toInstant().toEpochMilli(),
        ).map { Instant.ofEpochMilli(it.startUtcMillis).atZone(paris).toLocalDate() }

    private fun bothImports(rrule: String, start: LocalDateTime) = listOf(fromFile(rrule, start), fromDevice(rrule, start))

    @Test
    fun `the second Tuesday of the month recurs on the second Tuesday, through both imports`() {
        bothImports("FREQ=MONTHLY;BYDAY=2TU", LocalDateTime.of(2026, 11, 10, 9, 0)).forEach { event ->
            assertThat(datesBetween(event, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)))
                .containsExactly(LocalDate.of(2026, 12, 8))
        }
    }

    @Test
    fun `every weekday said with DAILY leaves the weekend out, through both imports`() {
        bothImports("FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR", LocalDateTime.of(2026, 11, 16, 9, 0)).forEach { event ->
            assertThat(datesBetween(event, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23))).containsExactly(
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 17),
                LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 19),
                LocalDate.of(2026, 11, 20),
            ).inOrder()
        }
    }

    @Test
    fun `a rule neither import can follow keeps its first date and invents no other`() {
        // The first and third Tuesday: not representable here. It used to become "the 3rd of every month".
        bothImports("FREQ=MONTHLY;BYDAY=1TU,3TU", LocalDateTime.of(2026, 11, 3, 9, 0)).forEach { event ->
            assertThat(datesBetween(event, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 1)))
                .containsExactly(LocalDate.of(2026, 11, 3))
        }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}
