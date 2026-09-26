package unfair.ui.clickgui.panel.settings

import unfair.Unfair
import unfair.management.ClientSettings
import unfair.property.Property
import unfair.property.properties.FloatProperty
import unfair.property.properties.IntProperty
import unfair.property.properties.PercentProperty
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class SliderSetting(private val value: Property<*>) : PanelValueItem() {
    private var dragging = false
    private var displayValue = 0f

    init {
        this.displayValue = this.`val`.toFloat()
    }

    private val min: Double
        get() {
            if (value is FloatProperty) return value.minimum
                .toDouble()
            if (value is IntProperty) return value.minimum
                .toDouble()
            if (value is PercentProperty) return value.minimum.toDouble()
            return 0.0
        }

    private val max: Double
        get() {
            if (value is FloatProperty) return value.maximum
                .toDouble()
            if (value is IntProperty) return value.maximum
                .toDouble()
            if (value is PercentProperty) return value.maximum.toDouble()
            return 100.0
        }

    private var `val`: Double
        get() {
            if (value is FloatProperty) return value.value
                .toDouble()
            if (value is IntProperty) return value.value
                .toDouble()
            if (value is PercentProperty) return value.value.toDouble()
            return 0.0
        }
        set(`val`) {
            if (value is FloatProperty) value.setValue(`val`.toFloat())
            else if (value is IntProperty) value.setValue(
                Math.round(`val`).toInt()
            )
            else if (value is PercentProperty) value.setValue(Math.round(`val`).toInt())
        }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(visible())
        displayValue = lerp(displayValue, this.`val`.toFloat(), 0.12f, deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        Unfair.fontManager!!.getFont(16).drawString(
            value.name, x, y + 1,
            blendAlpha(ClientSettings.INSTANCE.settingNameColor, alpha * visAlpha).rgb, false
        )

        val displayStr: String?
        if (value is FloatProperty || value is PercentProperty) displayStr =
            (Math.round(displayValue * 100.0) / 100.0).toString()
        else displayStr = Math.round(displayValue).toString()

        val textWidth = Unfair.fontManager!!.getFont(16).getStringWidth(displayStr).toFloat()
        Unfair.fontManager!!.getFont(16).drawString(
            displayStr, x + width - textWidth, y + 1,
            blendAlpha(ClientSettings.INSTANCE.valueTextColor, alpha * visAlpha).rgb, false
        )

        val sliderX = x
        val sliderW = width
        val sliderY = y + 17
        val sliderH = 2f

        drawRound(
            sliderX,
            sliderY,
            sliderW,
            sliderH,
            2f,
            blendAlpha(ClientSettings.INSTANCE.sliderTrackColor, alpha * visAlpha)
        )

        val range = this.max - this.min
        var fillW = if (range > 0) (sliderW * ((displayValue - this.min) / range)).toFloat() else 0f
        fillW = max(0f, min(fillW, sliderW))

        if (fillW > 0) {
            drawRound(
                sliderX,
                sliderY,
                fillW,
                sliderH,
                2f,
                blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha)
            )
        }

        val handleSize = 8f
        var handleX = sliderX + fillW - handleSize / 2f
        handleX = max(sliderX - handleSize / 2f, min(handleX, sliderX + sliderW - handleSize / 2f))
        drawRound(
            handleX, sliderY - (handleSize - sliderH) / 2f, handleSize, handleSize, handleSize / 2f,
            blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha)
        )

        if (dragging) {
            val pct = max(0f, min(1f, (mouseX - x) / width)).toDouble()
            var newVal = this.min + pct * range
            if (value is FloatProperty) newVal = Math.round(newVal * 100.0) / 100.0
            else newVal = Math.round(newVal).toDouble()
            if (newVal < this.min) newVal = this.min
            if (newVal > this.max) newVal = this.max
            this.`val` = newVal
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        val sliderY = y + 17
        if (isHovering(mx, my, x, sliderY - 4, width, 10f) && button == 0) dragging = true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {
        if (button == 0) dragging = false
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}
    override fun getHeight(): Float {
        return 26f
    }

    override fun visible(): Boolean {
        return value.isVisible
    }

    companion object {
        private fun blendAlpha(c: Color, alpha: Float): Color {
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt())
        }
    }
}
