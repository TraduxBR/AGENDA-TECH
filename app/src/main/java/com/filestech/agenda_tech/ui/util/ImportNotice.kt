package com.filestech.agenda_tech.ui.util

import android.content.res.Resources
import com.filestech.agenda_tech.R

/**
 * [outcome] — what an import says it did — followed, on a line of its own, by how many repeating events
 * it took as their first date only, when there are any: a rule the app cannot reproduce is imported
 * that way rather than on dates nobody chose, and the user has to be told which way it went.
 *
 * One function for the two imports (`.ics` and device calendar), so the sentence cannot be said by one
 * and forgotten by the other. A line of its own rather than words woven into [outcome]: it qualifies
 * every outcome the same way, a partial or truncated import included.
 */
fun Resources.withFirstDateOnly(outcome: String, firstDateOnly: Int): String =
    if (firstDateOnly > 0) {
        outcome + "\n" + getQuantityString(R.plurals.import_first_date_only, firstDateOnly, firstDateOnly)
    } else {
        outcome
    }
