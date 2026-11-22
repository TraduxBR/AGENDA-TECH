package com.filestech.agenda_tech.domain.usecase

import com.filestech.agenda_tech.domain.model.Event

/**
 * True when a re-import must leave [stored] exactly as it is: the incoming copy repeats by a rule this
 * app cannot reproduce, so it would arrive as its first date only, and the row it would update is a series.
 *
 * Updating it would turn the series into a single event — its cancelled occurrences gone, and the
 * occurrences moved in this app (rows pointing at it through `recurrenceParentId`) orphaned. Nothing
 * undoes that. So the row is not touched at all, not even its title, and the import counts it with the
 * other first-date-only events, which is what tells the user.
 *
 * One answer for both imports (`.ics` and device calendar): two paths that update by identity must not
 * disagree about when not to.
 */
internal fun keepsStoredSeries(stored: Event, incomingFirstDateOnly: Boolean): Boolean =
    incomingFirstDateOnly && stored.recurrence != null
