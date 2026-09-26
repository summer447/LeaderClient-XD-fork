package unfair.util

import java.awt.Color
import kotlin.math.max
import kotlin.math.min

object ColorUtil {
    val RED: Color = Color(255, 0, 0)
    val GOLD: Color = Color(255, 165, 0)
    val YELLOW: Color = Color(255, 255, 0)
    val GREEN: Color = Color(0, 255, 0)

    @JvmStatic
    fun fromHSB(hue: Float, saturation: Float, brightness: Float): Color {
        return Color(Color.HSBtoRGB(hue, saturation, brightness))
    }

    @JvmStatic
    fun interpolate(progress: Float, startColor: Color, endColor: Color): Color {
        var progress = progress
        progress = min(max(progress, 0.0f), 1.0f)
        return Color(
            (startColor.red.toFloat() + progress * (endColor.red - startColor.red).toFloat()).toInt(),
            (startColor.green
                .toFloat() + progress * (endColor.green - startColor.green).toFloat()).toInt(),
            (startColor.blue.toFloat() + progress * (endColor.blue - startColor.blue).toFloat()).toInt()
        )
    }

    @JvmStatic
    fun getHealthBlend(percent: Float): Color {
        if (percent >= 0.9f) {
            return GREEN
        }
        if (percent >= 0.55f) {
            return interpolate((percent - 0.55f) / 0.35f, YELLOW, GREEN)
        }
        if (percent >= 0.45f) {
            return YELLOW
        }
        if (percent >= 0.1f) {
            return interpolate((percent - 0.1f) / 0.35f, RED, YELLOW)
        }
        return RED
    }

    @JvmStatic
    fun darker(color: Color, factor: Float): Color {
        return scale(color, factor, color.alpha)
    }

    fun scale(color: Color, scaleFactor: Float, alpha: Int): Color {
        return Color(
            min(max((color.red.toFloat() * scaleFactor).toInt(), 0), 255),
            min(max((color.green.toFloat() * scaleFactor).toInt(), 0), 255),
            min(max((color.blue.toFloat() * scaleFactor).toInt(), 0), 255),
            alpha
        )
    }

    @JvmStatic
    fun applyOpacity(color: Color, opacity: Float): Color {
        var opacity = opacity
        opacity = min(1.0f, max(0.0f, opacity))
        return Color(color.red, color.green, color.blue, (255 * opacity).toInt())
    }
}
