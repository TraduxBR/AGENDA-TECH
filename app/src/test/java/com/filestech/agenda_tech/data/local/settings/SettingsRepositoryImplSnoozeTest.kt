package com.filestech.agenda_tech.data.local.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.filestech.agenda_tech.domain.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The snooze delay through a real DataStore file: a setting read but never written — or written under
 * one key and read under another — passes every test that uses a fake repository.
 */
class SettingsRepositoryImplSnoozeTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }
    }

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun repository() = SettingsRepositoryImpl(dataStore)

    @Test
    fun `an install from before the setting reads ten minutes, the delay it always had`() = runBlocking {
        assertThat(repository().current().snoozeMinutes).isEqualTo(10)
    }

    @Test
    fun `a chosen delay reads back, and survives a change to another setting`() = runBlocking {
        val repository = repository()
        repository.update { it.copy(snoozeMinutes = 30) }
        repository.update { it.copy(notifVibrate = false) }

        assertThat(repository.current().snoozeMinutes).isEqualTo(30)
    }

    @Test
    fun `a stored value that is not one of the choices reads as the default`() = runBlocking {
        // Written by a later version with other choices, say: never an alarm at an arbitrary delay.
        dataStore.edit { it[intPreferencesKey("snooze_minutes")] = 0 }

        assertThat(repository().current().snoozeMinutes).isEqualTo(AppSettings.DEFAULT_SNOOZE_MINUTES)
    }
}
