package unfair.ui.clickgui.panel.config

import unfair.Unfair
import unfair.management.ClientSettings
import unfair.management.ClientSettings.Theme
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class SettingsEntry(private val name: String?, private val type: SettingsType) : PanelValueItem() {
    private var value: Any? = null

    private var hoverAnim = 0f
    private var hovered = false
    private var sliderDragging = false

    private var toggleAnim = 0f
    private var sliderDisplayAlpha = 0f

    enum class SettingsType {
        THEME,
        BACKGROUND_ALPHA,
        BLUR_AREA
    }

    init {
        updateValue()
    }

    private fun updateValue() {
        when (type) {
            SettingsType.THEME -> value = ClientSettings.INSTANCE.theme
            SettingsType.BACKGROUND_ALPHA -> value = ClientSettings.INSTANCE.backgroundAlpha
            SettingsType.BLUR_AREA -> value = ClientSettings.INSTANCE.isBlurArea
        }
    }

    override fun update(deltaTime: Float) {
        val target = if (hovered) 1f else 0f
        hoverAnim = lerp(hoverAnim, target, 0.15f, deltaTime)

        if (type == SettingsType.BLUR_AREA) {
            val on = ClientSettings.INSTANCE.isBlurArea
            val tTarget = if (on) 1f else 0f
            toggleAnim = lerp(toggleAnim, tTarget, 0.18f, deltaTime)
        }

        if (type == SettingsType.BACKGROUND_ALPHA) {
            val sTarget = if (sliderDragging) 1f else 0f
            sliderDisplayAlpha = lerp(sliderDisplayAlpha, sTarget, 0.15f, deltaTime)
        }
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val entryHeight = height
        hovered = isHovering(mouseX, mouseY, x, y, width, entryHeight)

        val bgColor: Color = blendColor(
            ClientSettings.INSTANCE.entryBgColor,
            ClientSettings.INSTANCE.entryHoverColor,
            hoverAnim
        )
        drawRound(x, y, width, entryHeight, 5f, blendAlpha(bgColor, alpha))

        Unfair.fontManager!!.getFont(18).drawString(
            name, x + 12, y + 6,
            blendAlpha(ClientSettings.INSTANCE.titleMainColor, alpha).rgb, false
        )

        if (type == SettingsType.BACKGROUND_ALPHA) {
            drawSlider(mouseX)
        } else if (type == SettingsType.BLUR_AREA) {
            drawToggle()
        } else {
            val valueText = this.valueText
            val textW = Unfair.fontManager!!.getFont(18).getStringWidth(valueText).toFloat()
            Unfair.fontManager!!.getFont(18).drawString(
                valueText, x + width - textW - 12, y + 7,
                blendAlpha(ClientSettings.INSTANCE.entryValueColor, alpha).rgb, false
            )
        }
    }

    private fun drawSlider(mouseX: Int) {
        val alphaVal = ClientSettings.INSTANCE.backgroundAlpha
        val sliderX = x + 12
        val sliderY = y + 28
        val sliderW = width - 24
        val sliderH = 2f

        drawRound(
            sliderX, sliderY, sliderW, sliderH, 2f,
            blendAlpha(ClientSettings.INSTANCE.sliderTrackColor, alpha)
        )

        val fillW = (alphaVal / 255f) * sliderW
        if (fillW > 0) {
            drawRound(
                sliderX, sliderY, fillW, sliderH, 2f,
                blendAlpha(ClientSettings.INSTANCE.accentColor, alpha)
            )
        }

        val dotSize = 12 + sliderDisplayAlpha * 4
        val dotX = max(sliderX, min(sliderX + sliderW, sliderX + fillW))
        val dotColor: Color = blendColor(
            ClientSettings.INSTANCE.accentColor,
            ClientSettings.INSTANCE.cardColor,
            sliderDisplayAlpha
        )
        drawRound(
            dotX - dotSize / 2f, sliderY + (sliderH - dotSize) / 2f,
            dotSize, dotSize, dotSize / 2f, blendAlpha(dotColor, alpha)
        )

        if (sliderDragging) {
            var relativeX = mouseX - sliderX
            relativeX = max(0f, min(sliderW, relativeX))
            val newAlpha = Math.round((relativeX / sliderW) * 255)
            ClientSettings.INSTANCE.backgroundAlpha = newAlpha
            value = newAlpha
        }
    }

    private fun drawToggle() {
        val toggleW = 44f
        val toggleH = 20f
        val toggleX = x + width - toggleW - 12
        val toggleY = y + (height - toggleH) / 2f

        val trackColor: Color = blendColor(
            ClientSettings.INSTANCE.toggleOffColor,
            ClientSettings.INSTANCE.accentColor,
            toggleAnim
        )
        drawRound(
            toggleX, toggleY, toggleW, toggleH, toggleH / 2f,
            blendAlpha(trackColor, alpha)
        )

        val dotSize = toggleH - 4
        val dotX = toggleX + 2 + toggleAnim * (toggleW - dotSize - 4)
        val dotY = toggleY + 2
        drawRound(
            dotX, dotY, dotSize, dotSize, dotSize / 2f,
            blendAlpha(ClientSettings.INSTANCE.cardColor, alpha)
        )
    }

    private val valueText: String?
        get() {
            when (type) {
                SettingsType.THEME -> return ClientSettings.INSTANCE.theme.displayName
                else -> return ""
            }
        }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button != 0) return

        val entryHeight = height
        if (!isHovering(mx, my, x, y, width, entryHeight)) return

        when (type) {
            SettingsType.THEME -> {
                val themes: Array<Theme> = Theme.entries.toTypedArray()
                val currentThemeIndex = (value as Theme).ordinal
                val nextThemeIndex = (currentThemeIndex + 1) % themes.size
                ClientSettings.INSTANCE.theme = themes[nextThemeIndex]
                value = themes[nextThemeIndex]
            }

            SettingsType.BACKGROUND_ALPHA -> sliderDragging = true
            SettingsType.BLUR_AREA -> {
                ClientSettings.INSTANCE.isBlurArea = !ClientSettings.INSTANCE.isBlurArea
                value = ClientSettings.INSTANCE.isBlurArea
            }
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {
        if (button == 0) {
            sliderDragging = false
        }
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}

    override fun getHeight(): Float {
        return (if (type == SettingsType.BACKGROUND_ALPHA) 44 else 30).toFloat()
    }

    override fun visible(): Boolean {
        return true
    }

    companion object {
        private fun blendColor(a: Color, b: Color, t: Float): Color {
            val r = (a.red + (b.red - a.red) * t).toInt()
            val g = (a.green + (b.green - a.green) * t).toInt()
            val bl = (a.blue + (b.blue - a.blue) * t).toInt()
            return Color(r, g, bl)
        }

        private fun blendAlpha(c: Color, alpha: Float): Color {
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt())
        }
    }
}
