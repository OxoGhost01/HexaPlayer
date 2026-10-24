package com.oxoghost.hexaplayer.ui.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.TerminalPaletteTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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
            // Before 2.0 phase 5, picking the "Album art" swatch saved it as a fixed preset.
            materialSource = p[Keys.MATERIAL_SOURCE].toEnum(defaults.materialSource).let { source ->
                if (source == MaterialSource.Preset && p[Keys.MATERIAL_PRESET] == MaterialSchemes.ALBUM_ART) MaterialSource.AlbumArt else source
            },
            materialPreset = p[Keys.MATERIAL_PRESET] ?: defaults.materialPreset,
            pureBlack = p[Keys.PURE_BLACK] ?: defaults.pureBlack,
            tintPlayerWithCover = p[Keys.TINT_PLAYER] ?: defaults.tintPlayerWithCover,
            pixelCovers = p[Keys.PIXEL_COVERS] ?: defaults.pixelCovers,
            customFlavors = p[Keys.CUSTOM_FLAVORS]?.let(::parseFlavors).orEmpty(),
        )
    }

    suspend fun setSkin(value: Skin) = put(Keys.SKIN, value.name)
    suspend fun setTerminalPalette(value: String) = put(Keys.TERMINAL_PALETTE, value)
    suspend fun setMaterialSource(value: MaterialSource) {
        store.edit {
            it[Keys.MATERIAL_SOURCE] = value.name
            if (value == MaterialSource.Preset && it[Keys.MATERIAL_PRESET] == MaterialSchemes.ALBUM_ART) {
                it[Keys.MATERIAL_PRESET] = DesignTokens.Material.DEFAULT_SCHEME
            }
        }
    }
    suspend fun setMaterialPreset(value: String) = put(Keys.MATERIAL_PRESET, value)
    suspend fun setPureBlack(value: Boolean) = put(Keys.PURE_BLACK, value)
    suspend fun setTintPlayerWithCover(value: Boolean) = put(Keys.TINT_PLAYER, value)
    suspend fun setPixelCovers(value: Boolean) = put(Keys.PIXEL_COVERS, value)

    /** Saves a custom flavor (replacing [previousName] when renamed) and selects it. */
    suspend fun saveFlavor(name: String, colors: TerminalPaletteTokens, previousName: String?) {
        store.edit {
            val flavors = it[Keys.CUSTOM_FLAVORS]?.let(::parseFlavors).orEmpty().toMutableMap()
            if (previousName != null) flavors.remove(previousName)
            flavors[name] = colors
            it[Keys.CUSTOM_FLAVORS] = writeFlavors(flavors)
            it[Keys.TERMINAL_PALETTE] = name
        }
    }

    suspend fun deleteFlavor(name: String) {
        store.edit {
            val flavors = it[Keys.CUSTOM_FLAVORS]?.let(::parseFlavors).orEmpty() - name
            it[Keys.CUSTOM_FLAVORS] = writeFlavors(flavors)
            if (it[Keys.TERMINAL_PALETTE] == name) it[Keys.TERMINAL_PALETTE] = DesignTokens.Terminal.DEFAULT_PALETTE
        }
    }

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
        val CUSTOM_FLAVORS = stringPreferencesKey("custom_flavors")
    }
}

/** The 11 terminal color roles, in the order the editor lists them. */
val flavorRoles = listOf("background", "panel", "selection", "foreground", "dim", "accent", "accent2", "ok", "warn", "error", "border")

fun TerminalPaletteTokens.roles(): List<Color> = listOf(background, panel, selection, foreground, dim, accent, accent2, ok, warn, error, border)

fun List<Color>.toPalette(): TerminalPaletteTokens =
    TerminalPaletteTokens(this[0], this[1], this[2], this[3], this[4], this[5], this[6], this[7], this[8], this[9], this[10])

private fun parseFlavors(json: String): Map<String, TerminalPaletteTokens> = runCatching {
    val obj = JSONObject(json)
    obj.keys().asSequence().mapNotNull { name ->
        val arr = obj.getJSONArray(name)
        if (arr.length() != flavorRoles.size) return@mapNotNull null
        name to List(arr.length()) { Color(arr.getLong(it).toInt()) }.toPalette()
    }.toMap()
}.getOrDefault(emptyMap())

private fun writeFlavors(flavors: Map<String, TerminalPaletteTokens>): String {
    val obj = JSONObject()
    flavors.forEach { (name, p) -> obj.put(name, JSONArray(p.roles().map { it.toArgb().toLong() and 0xFFFFFFFFL })) }
    return obj.toString()
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
