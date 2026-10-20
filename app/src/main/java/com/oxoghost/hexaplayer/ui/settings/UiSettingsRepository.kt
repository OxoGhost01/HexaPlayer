package com.oxoghost.hexaplayer.ui.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_settings",
    produceMigrations = { context -> listOf(AccentMigration(context)) },
)

/** UI settings (skin, palettes, pure black...) stored in DataStore. */
class UiSettingsRepository(context: Context) {

    private val store = context.applicationContext.uiDataStore

    val settings: Flow<UiSettings> = store.data.map { p ->
        val defaults = UiSettings()
        UiSettings(
            skin = p[Keys.SKIN].toEnum(defaults.skin),
            terminalPalette = p[Keys.TERMINAL_PALETTE] ?: defaults.terminalPalette,
            materialSource = p[Keys.MATERIAL_SOURCE].toEnum(defaults.materialSource),
            materialPreset = p[Keys.MATERIAL_PRESET] ?: defaults.materialPreset,
            pureBlack = p[Keys.PURE_BLACK] ?: defaults.pureBlack,
            tintPlayerWithCover = p[Keys.TINT_PLAYER] ?: defaults.tintPlayerWithCover,
            pixelCovers = p[Keys.PIXEL_COVERS] ?: defaults.pixelCovers,
        )
    }

    suspend fun setSkin(value: Skin) = put(Keys.SKIN, value.name)
    suspend fun setTerminalPalette(value: String) = put(Keys.TERMINAL_PALETTE, value)
    suspend fun setMaterialSource(value: MaterialSource) = put(Keys.MATERIAL_SOURCE, value.name)
    suspend fun setMaterialPreset(value: String) = put(Keys.MATERIAL_PRESET, value)
    suspend fun setPureBlack(value: Boolean) = put(Keys.PURE_BLACK, value)
    suspend fun setTintPlayerWithCover(value: Boolean) = put(Keys.TINT_PLAYER, value)
    suspend fun setPixelCovers(value: Boolean) = put(Keys.PIXEL_COVERS, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    internal object Keys {
        val SKIN = stringPreferencesKey("skin")
        val TERMINAL_PALETTE = stringPreferencesKey("terminal_palette")
        val MATERIAL_SOURCE = stringPreferencesKey("material_source")
        val MATERIAL_PRESET = stringPreferencesKey("material_preset")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val TINT_PLAYER = booleanPreferencesKey("tint_player_with_cover")
        val PIXEL_COVERS = booleanPreferencesKey("pixel_covers")
        val MIGRATED_1X = booleanPreferencesKey("migrated_1x")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

/**
 * One-time import of the 1.x accent (hexa_prefs > accent_index) as the material preset.
 * The 1.x value is left in place: the View-based screens still read it until they are replaced.
 */
private class AccentMigration(private val context: Context) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[UiSettingsRepository.Keys.MIGRATED_1X] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = context.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)
        val out: MutablePreferences = currentData.toMutablePreferences()
        if (prefs.contains("accent_index")) {
            out[UiSettingsRepository.Keys.MATERIAL_PRESET] = when (prefs.getInt("accent_index", 0)) {
                2 -> "Ocean"
                3 -> "Lagoon"
                4 -> "Rose"
                5 -> "Amber"
                else -> "Hexa"
            }
        }
        out[UiSettingsRepository.Keys.MIGRATED_1X] = true
        return out.toPreferences()
    }

    override suspend fun cleanUp() {}
}
