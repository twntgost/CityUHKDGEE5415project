package com.example.hrrepayment.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.hrrepayment.core.i18n.Lang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Device-level settings that must survive both a restart and a save-slot change.
 *
 * Only the language lives here today. It is deliberately **not** part of [GameState]:
 * the language is a property of the player, not of a run, and storing it in the save
 * would make “switch language, then load the other slot” behave inconsistently.
 */
class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val languageKey = stringPreferencesKey("language")
    private val soundKey = booleanPreferencesKey("sound_enabled")

    /** Cold flow of the chosen language; never throws and never completes. */
    val language: Flow<Lang> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { Lang.from(it[languageKey]) }

    /** 音效开关，默认开启。 */
    val soundEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[soundKey] ?: true }

    suspend fun currentSoundEnabled(): Boolean = try {
        dataStore.data.first()[soundKey] ?: true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        true
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[soundKey] = enabled }
    }

    suspend fun currentLanguage(): Lang = try {
        Lang.from(dataStore.data.first()[languageKey])
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Lang.DEFAULT
    }

    suspend fun setLanguage(lang: Lang) {
        dataStore.edit { it[languageKey] = lang.code }
    }

    companion object {
        private const val FILE_NAME = "settings.preferences_pb"

        fun create(context: Context): SettingsRepository =
            create(context.preferencesDataStoreFile(FILE_NAME))

        fun create(
            file: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): SettingsRepository {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            return SettingsRepository(store)
        }
    }
}
