package com.filestech.agenda_tech.ui.util

import android.content.res.Resources
import com.filestech.agenda_tech.R
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/** The line both imports add when they took repeating events as their first date only. */
class ImportNoticeTest {

    private val resources: Resources = mockk {
        // The quantity stands for the formatted number: the real plural formats that same count.
        every { getQuantityString(R.plurals.import_first_date_only, any(), *anyVararg()) } answers {
            "${secondArg<Int>()} repeating events: first date only."
        }
    }

    @Test
    fun `an import that took no rule apart says what it said before, and nothing more`() {
        assertThat(resources.withFirstDateOnly("12 events imported", 0)).isEqualTo("12 events imported")
        verify(exactly = 0) { resources.getQuantityString(any(), any(), *anyVararg()) }
    }

    @Test
    fun `the count is said on a line of its own, after the outcome it qualifies`() {
        assertThat(resources.withFirstDateOnly("12 events imported", 2))
            .isEqualTo("12 events imported\n2 repeating events: first date only.")
    }
}
