package com.yash.tracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yash.tracker.data.backup.BackupCodeStore
import com.yash.tracker.data.remote.GeminiConfig
import com.yash.tracker.domain.model.HeightUnit
import com.yash.tracker.domain.model.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Light first, and light by default: the palette was designed on paper-white and that is the
 * app's own look. Following the system is offered, but it is a choice rather than the fallback.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore,
) : GeminiConfig, BackupCodeStore {
    private val store = context.dataStore

    val themeMode: Flow<ThemeMode> = store.data.map {
        runCatching { ThemeMode.valueOf(it[THEME] ?: ThemeMode.LIGHT.name) }
            .getOrDefault(ThemeMode.LIGHT)
    }

    val weightUnit: Flow<WeightUnit> = store.data.map {
        runCatching { WeightUnit.valueOf(it[WEIGHT_UNIT] ?: WeightUnit.KG.name) }
            .getOrDefault(WeightUnit.KG)
    }

    val heightUnit: Flow<HeightUnit> = store.data.map {
        runCatching { HeightUnit.valueOf(it[HEIGHT_UNIT] ?: HeightUnit.CM.name) }
            .getOrDefault(HeightUnit.CM)
    }

    val groundingEnabled: Flow<Boolean> = store.data.map { it[GROUNDING] ?: true }

    override suspend fun model(): String = DEFAULT_MODEL

    override suspend fun isGroundingEnabled(): Boolean = groundingEnabled.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    suspend fun setWeightUnit(unit: WeightUnit) = store.edit { it[WEIGHT_UNIT] = unit.name }

    suspend fun setHeightUnit(unit: HeightUnit) = store.edit { it[HEIGHT_UNIT] = unit.name }

    suspend fun setGroundingEnabled(enabled: Boolean) = store.edit { it[GROUNDING] = enabled }

    /**
     * The backup code, encrypted at rest like the API key. Keeping it on the phone is only so
     * Settings can show it again — it is written down off the phone or it is not a backup code
     * at all, since the phone is the thing being backed up.
     */
    override suspend fun backupCode(): String? {
        val stored = store.data.first()[BACKUP_CODE] ?: return null
        return secureKeyStore.decrypt(stored)
    }

    override suspend fun setBackupCode(code: String) {
        store.edit { prefs -> prefs[BACKUP_CODE] = secureKeyStore.encrypt(code) }
    }

    private companion object {
        // Flash-Lite is the cheapest tier that still does vision and structured output:
        // $0.30 per M input tokens against $1.50 for Flash. Verified present on the live
        // model list.
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

        val THEME = stringPreferencesKey("theme_mode")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val HEIGHT_UNIT = stringPreferencesKey("height_unit")
        val GROUNDING = booleanPreferencesKey("grounding_enabled")
        val BACKUP_CODE = stringPreferencesKey("backup_code")
    }
}
