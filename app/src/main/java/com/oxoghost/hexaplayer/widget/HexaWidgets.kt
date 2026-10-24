package com.oxoghost.hexaplayer.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.palette.graphics.Palette
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.service.MusicService
import com.oxoghost.hexaplayer.ui.MainActivity
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes.pureBlack
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalPalettes
import kotlinx.coroutines.flow.first

/** The colors a widget needs, taken from the app's current skin and palette. */
private data class WidgetColors(
    val terminal: Boolean,
    val surface: Color,
    val onSurface: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val button: Color,
    val onButton: Color,
    val border: Color,
    val track: Color,
)

private fun colorsFor(context: Context, settings: UiSettings, art: Bitmap?): WidgetColors {
    if (settings.skin == Skin.Terminal) {
        val c = TerminalPalettes.get(settings.terminalPalette, settings.pureBlack, settings.customFlavors)
        return WidgetColors(true, c.background, c.foreground, c.dim, c.accent, c.background, c.background, c.foreground, c.border, c.border)
    }
    val base = when (settings.materialSource) {
        MaterialSource.Preset -> MaterialSchemes.preset(settings.materialPreset)
        MaterialSource.Wallpaper -> MaterialSchemes.wallpaper(context, settings.materialPreset)
        MaterialSource.AlbumArt -> art?.let { Palette.from(it).generate() }
            ?.let { it.vibrantSwatch ?: it.dominantSwatch }
            ?.let { MaterialSchemes.fromSeed(Color(it.rgb)) }
            ?: MaterialSchemes.preset(settings.materialPreset)
    }
    val s = if (settings.pureBlack) base.pureBlack() else base
    return WidgetColors(
        false, s.surfaceContainer, s.onSurface, s.onSurfaceVariant, s.primaryContainer, s.onPrimaryContainer,
        s.secondaryContainer, s.onSecondaryContainer, s.outlineVariant, s.secondaryContainer,
    )
}

/** Everything a widget shows, loaded off the main thread. */
private data class WidgetModel(val data: WidgetData, val colors: WidgetColors, val cover: Bitmap?)

private suspend fun loadModel(context: Context, coverDp: Int, cells: Int): WidgetModel {
    val data = WidgetController.read(context)
    val settings = UiSettingsRepository(context).settings.first()
    val px = (coverDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val art = WidgetController.loadAlbumArt(context, data.albumId, px)
    val colors = colorsFor(context, settings, art)
    val cover = art?.let {
        when {
            colors.terminal && settings.pixelCovers -> WidgetController.rounded(WidgetController.pixelate(it, cells, px), 6 * context.resources.displayMetrics.density)
            colors.terminal -> it
            else -> WidgetController.rounded(it, px * 0.2f)
        }
    }
    return WidgetModel(data, colors, cover)
}

private fun serviceAction(context: Context, action: String): Action =
    actionStartService(Intent(action).setComponent(ComponentName(context, MusicService::class.java)))

private fun ColorProvider(color: Color) = androidx.glance.color.ColorProvider(color, color)

private fun text(color: Color, size: TextUnit, bold: Boolean, mono: Boolean) = TextStyle(
    color = ColorProvider(color),
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = if (mono) FontFamily.Monospace else null,
)

/** 4x1: cover, title, artist, progress and the three controls. */
class WideWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadModel(context, 96, 12)
        provideContent { Wide(context, rememberModel(context, first, 96, 12)) }
    }
}

/** 5x1: a single row with cover, title and controls. */
class SlimWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = loadModel(context, 56, 8)
        provideContent { Slim(context, rememberModel(context, first, 56, 8)) }
    }
}

/** Bumped by [WidgetController.updateAllWidgets] so a running widget session reloads its data. */
internal val VersionKey = longPreferencesKey("version")

@Composable
private fun rememberModel(context: Context, first: WidgetModel, coverDp: Int, cells: Int): WidgetModel {
    val version = currentState<Preferences>()[VersionKey] ?: 0L
    val model by produceState(first, version) { value = loadModel(context, coverDp, cells) }
    return model
}

class HexaWidget4x1 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}

class HexaWidget5x1 : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SlimWidget()
}

@Composable
private fun Frame(model: WidgetModel, radius: Dp, content: @Composable () -> Unit) {
    val c = model.colors
    // Terminal widgets carry a 1 dp border: an outer box in the border color around the content.
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(radius)
            .background(ColorProvider(if (c.terminal) c.border else c.surface))
            .padding(if (c.terminal) 1.dp else 0.dp)
    ) {
        Box(GlanceModifier.fillMaxSize().cornerRadius(radius).background(ColorProvider(c.surface))) { content() }
    }
}

@Composable
private fun Cover(model: WidgetModel, size: Dp) {
    val c = model.colors
    val cover = model.cover
    if (cover != null) {
        Image(ImageProvider(cover), "Album cover", GlanceModifier.size(size))
    } else {
        Box(
            GlanceModifier.size(size).cornerRadius(if (c.terminal) 6.dp else size * 0.2f).background(ColorProvider(c.button)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_music_note), null, GlanceModifier.size(size * 0.45f), colorFilter = ColorFilter.tint(ColorProvider(c.dim)))
        }
    }
}

/** A control key: square bordered cell (terminal) or tonal pill (material); [primary] is the play key. */
@Composable
private fun RowScope.Key(context: Context, model: WidgetModel, icon: Int, label: String, action: String, primary: Boolean, height: Dp, weight: Boolean = true) {
    val c = model.colors
    val bg = when {
        primary -> c.accent
        c.terminal -> c.border
        else -> c.button
    }
    val fg = when {
        primary -> c.onAccent
        else -> c.onButton
    }
    val sizing = if (weight) GlanceModifier.defaultWeight() else GlanceModifier.width(height)
    Box(
        sizing
            .height(height)
            .cornerRadius(if (c.terminal) 0.dp else height / 2)
            .background(ColorProvider(bg))
            .padding(if (c.terminal && !primary) 1.dp else 0.dp)
            .clickable(serviceAction(context, action)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            GlanceModifier.fillMaxSize().background(ColorProvider(if (c.terminal && !primary) c.surface else bg)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), label, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(ColorProvider(fg)))
        }
    }
}

@Composable
private fun Idle(context: Context, model: WidgetModel) {
    val c = model.colors
    Row(
        GlanceModifier.fillMaxSize().padding(horizontal = 16.dp).clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.terminal) {
            Text("hexa ", style = text(c.accent, 15.sp, true, true))
            Text("nothing playing", GlanceModifier.defaultWeight(), style = text(c.dim, 15.sp, false, true))
        } else {
            Image(ImageProvider(R.drawable.ic_music_note), null, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(ColorProvider(c.accent)))
            Spacer(GlanceModifier.width(12.dp))
            Text("Nothing playing", GlanceModifier.defaultWeight(), style = text(c.onSurface, 16.sp, false, false))
        }
        Box(
            GlanceModifier
                .height(44.dp)
                .cornerRadius(if (c.terminal) 0.dp else 22.dp)
                .background(ColorProvider(c.accent))
                .padding(horizontal = 18.dp)
                .clickable(serviceAction(context, WidgetController.ACTION_RESUME)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (c.terminal) "▶ resume" else "Resume", style = text(c.onAccent, 15.sp, true, c.terminal))
        }
    }
}

@Composable
private fun Wide(context: Context, model: WidgetModel) {
    val c = model.colors
    val d = model.data
    Frame(model, if (c.terminal) 14.dp else 28.dp) {
        if (d.idle) {
            Idle(context, model)
            return@Frame
        }
        // One cell tall by default; taller placements get a bigger cover and keys.
        val height = LocalSize.current.height
        val cover = (height - 20.dp).coerceIn(48.dp, 96.dp)
        val keys = if (height < 100.dp) 32.dp else 40.dp
        Row(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.clickable(actionStartActivity<MainActivity>())) { Cover(model, cover) }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())) {
                    Text(
                        if (c.terminal && d.isPlaying) "▶ ${d.title}" else d.title,
                        style = text(c.onSurface, if (height < 100.dp) 14.sp else 16.sp, true, c.terminal), maxLines = 1,
                    )
                    Text(d.artist, style = text(c.dim, if (height < 100.dp) 12.sp else 13.sp, false, c.terminal), maxLines = 1)
                }
                if (c.terminal && height >= 100.dp) {
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(
                        d.fraction, GlanceModifier.fillMaxWidth().height(3.dp),
                        color = ColorProvider(c.accent), backgroundColor = ColorProvider(c.track),
                    )
                }
                Spacer(GlanceModifier.height(if (height < 100.dp) 4.dp else 8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Key(context, model, R.drawable.ic_skip_previous, "Previous", WidgetController.ACTION_PREVIOUS, false, keys)
                    Spacer(GlanceModifier.width(6.dp))
                    Key(context, model, if (d.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                        if (d.isPlaying) "Pause" else "Play", WidgetController.ACTION_PLAY_PAUSE, true, keys)
                    Spacer(GlanceModifier.width(6.dp))
                    Key(context, model, R.drawable.ic_skip_next, "Next", WidgetController.ACTION_NEXT, false, keys)
                }
            }
        }
    }
}

@Composable
private fun Slim(context: Context, model: WidgetModel) {
    val c = model.colors
    val d = model.data
    Frame(model, if (c.terminal) 14.dp else 36.dp) {
        if (d.idle) {
            Idle(context, model)
            return@Frame
        }
        Row(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.clickable(actionStartActivity<MainActivity>())) { Cover(model, 52.dp) }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                Text(d.title, style = text(c.onSurface, 15.sp, true, c.terminal), maxLines = 1)
                Text(
                    if (c.terminal && d.durationMs > 0) "${d.artist} · ${formatDuration(d.positionMs)}" else d.artist,
                    style = text(c.dim, 13.sp, false, c.terminal), maxLines = 1,
                )
            }
            Key(context, model, R.drawable.ic_skip_previous, "Previous", WidgetController.ACTION_PREVIOUS, false, 44.dp, weight = false)
            Spacer(GlanceModifier.width(6.dp))
            Key(context, model, if (d.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (d.isPlaying) "Pause" else "Play", WidgetController.ACTION_PLAY_PAUSE, true, 52.dp, weight = false)
            Spacer(GlanceModifier.width(6.dp))
            Key(context, model, R.drawable.ic_skip_next, "Next", WidgetController.ACTION_NEXT, false, 44.dp, weight = false)
        }
    }
}
