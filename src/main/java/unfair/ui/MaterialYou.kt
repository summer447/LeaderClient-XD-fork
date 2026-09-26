package unfair.ui

import unfair.ui.material.WallpaperColors
import unfair.ui.material.color.dynamiccolor.DynamicScheme
import unfair.ui.material.color.dynamiccolor.MaterialDynamicColors
import unfair.ui.material.color.hct.Hct
import unfair.ui.material.color.scheme.SchemeTonalSpot

/**
 * Material You (Material 3) dynamic color scheme.
 *
 * Mirrors lite-material-components-4a's Imc.publicColorScheme
 * (SchemeTonalSpot, contrast 0.0), but the seed color is extracted from the
 * desktop wallpaper at runtime — the same dynamic color pipeline Android 12+
 * uses. Falls back to the M3 baseline seed 0xFF6750A4 when no wallpaper image
 * can be read.
 */
object MaterialYou {

    /** M3 baseline purple — the same seed as Imc.publicColorScheme. */
    val FALLBACK_SEED = 0xFF6750A4.toInt()

    /** Seed color extracted from the desktop wallpaper. */
    val seedColor: Int by lazy { WallpaperColors.extractSeedColor(FALLBACK_SEED) }

    private val dynamicColors = MaterialDynamicColors()

    @JvmStatic
    val lightScheme: Scheme by lazy { fromDynamicScheme(SchemeTonalSpot(Hct.fromInt(seedColor), false, 0.0)) }

    @JvmStatic
    val darkScheme: Scheme by lazy { fromDynamicScheme(SchemeTonalSpot(Hct.fromInt(seedColor), true, 0.0)) }

    @JvmStatic
    var darkTheme: Boolean = true

    /** Active scheme, mirroring Imc.publicColorScheme (dark by default). */
    @JvmStatic
    val scheme: Scheme
        get() = if (darkTheme) darkScheme else lightScheme

    /** Pre-computes schemes; call from a background thread during client init. */
    fun prewarm() {
        lightScheme
        darkScheme
    }

    class Scheme(
        val primary: Int, val onPrimary: Int,
        val primaryContainer: Int, val onPrimaryContainer: Int,
        val secondary: Int, val onSecondary: Int,
        val secondaryContainer: Int, val onSecondaryContainer: Int,
        val tertiary: Int, val onTertiary: Int,
        val tertiaryContainer: Int, val onTertiaryContainer: Int,
        val surface: Int, val onSurface: Int,
        val surfaceContainerLowest: Int, val surfaceContainerLow: Int,
        val surfaceContainer: Int, val surfaceContainerHigh: Int,
        val surfaceContainerHighest: Int,
        val surfaceVariant: Int, val onSurfaceVariant: Int,
        val outline: Int, val outlineVariant: Int,
        val error: Int, val onError: Int,
        val scrim: Int
    )

    private fun fromDynamicScheme(ds: DynamicScheme): Scheme {
        val c = dynamicColors
        return Scheme(
            primary = c.primary().getArgb(ds),
            onPrimary = c.onPrimary().getArgb(ds),
            primaryContainer = c.primaryContainer().getArgb(ds),
            onPrimaryContainer = c.onPrimaryContainer().getArgb(ds),
            secondary = c.secondary().getArgb(ds),
            onSecondary = c.onSecondary().getArgb(ds),
            secondaryContainer = c.secondaryContainer().getArgb(ds),
            onSecondaryContainer = c.onSecondaryContainer().getArgb(ds),
            tertiary = c.tertiary().getArgb(ds),
            onTertiary = c.onTertiary().getArgb(ds),
            tertiaryContainer = c.tertiaryContainer().getArgb(ds),
            onTertiaryContainer = c.onTertiaryContainer().getArgb(ds),
            surface = c.surface().getArgb(ds),
            onSurface = c.onSurface().getArgb(ds),
            surfaceContainerLowest = c.surfaceContainerLowest().getArgb(ds),
            surfaceContainerLow = c.surfaceContainerLow().getArgb(ds),
            surfaceContainer = c.surfaceContainer().getArgb(ds),
            surfaceContainerHigh = c.surfaceContainerHigh().getArgb(ds),
            surfaceContainerHighest = c.surfaceContainerHighest().getArgb(ds),
            surfaceVariant = c.surfaceVariant().getArgb(ds),
            onSurfaceVariant = c.onSurfaceVariant().getArgb(ds),
            outline = c.outline().getArgb(ds),
            outlineVariant = c.outlineVariant().getArgb(ds),
            error = c.error().getArgb(ds),
            onError = c.onError().getArgb(ds),
            scrim = c.scrim().getArgb(ds)
        )
    }

    /** M3 state layer alphas (see MaterialButton / MaterialCardView state handling). */
    const val STATE_HOVER = 0.08f
    const val STATE_PRESS = 0.10f
    const val DISABLED_CONTAINER_ALPHA = 0.12f
    const val DISABLED_CONTENT_ALPHA = 0.38f

    /** Returns [color] with its alpha multiplied by [fraction]. */
    @JvmStatic
    fun withAlphaFraction(color: Int, fraction: Float): Int {
        val a = ((color ushr 24) and 0xFF)
        val newA = (a * fraction).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (newA shl 24)
    }

    /** Blends [stateColor] at [fraction] alpha over [base], keeping the result opaque. */
    @JvmStatic
    fun applyStateLayer(base: Int, stateColor: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = blendChannel(base, stateColor, 16, f)
        val g = blendChannel(base, stateColor, 8, f)
        val b = blendChannel(base, stateColor, 0, f)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun blendChannel(base: Int, overlay: Int, shift: Int, fraction: Float): Int {
        val bc = (base ushr shift) and 0xFF
        val oc = (overlay ushr shift) and 0xFF
        return (bc + (oc - bc) * fraction).toInt().coerceIn(0, 255)
    }
}
