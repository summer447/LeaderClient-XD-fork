package unfair.ui.clickgui.panel.settings

import jdk.nashorn.internal.objects.NativeMath.min
import unfair.Unfair
import unfair.management.ClientSettings
import unfair.property.properties.RangeProperty
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 双滑块设置项，对应 [RangeProperty]。
 */
class RangeSliderSetting(private val value: RangeProperty) : PanelValueItem() {
    // 0=未拖动 1=已按下但方向未定(两把手重合) 2=拖下界 3=拖上界
    private var dragState = 0
    private var anchorX = 0
    private var displayLow = value.low
    private var displayHigh = value.high

    private val range: Double
        get() = (value.maximum - value.minimum).toDouble()

    private fun pct(v: Float): Float =
        if (range > 0) ((v - value.minimum) / range).toFloat() else 0f

    private fun valueAtMouse(mouseX: Int): Float {
        val pct = max(0.0, min(1.0, (mouseX - x) / width))
        val v = (value.minimum + pct * range).toFloat()
        return Math.round(v * 100.0f) / 100.0f
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(visible())
        displayLow = lerp(displayLow, value.low, 0.12f, deltaTime)
        displayHigh = lerp(displayHigh, value.high, 0.12f, deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        Unfair.fontManager!!.getFont(16).drawString(
            value.name, x, y + 1,
            blendAlpha(ClientSettings.INSTANCE.settingNameColor, alpha * visAlpha).rgb, false
        )

        val displayStr = "${Math.round(displayLow * 100.0) / 100.0}-${Math.round(displayHigh * 100.0) / 100.0}"
        val textWidth = Unfair.fontManager!!.getFont(16).getStringWidth(displayStr).toFloat()
        Unfair.fontManager!!.getFont(16).drawString(
            displayStr, x + width - textWidth, y + 1,
            blendAlpha(ClientSettings.INSTANCE.valueTextColor, alpha * visAlpha).rgb, false
        )

        val sliderX = x
        val sliderW = width
        val sliderY = y + 17
        val sliderH = 2f

        // 轨道
        drawRound(
            sliderX, sliderY, sliderW, sliderH, 2f,
            blendAlpha(ClientSettings.INSTANCE.sliderTrackColor, alpha * visAlpha)
        )

        val lowX = sliderX + pct(displayLow) * sliderW
        val highX = sliderX + pct(displayHigh) * sliderW
        val fillW = max(0f, highX - lowX)

        // 选中区间
        if (fillW > 0) {
            drawRound(
                lowX, sliderY, fillW, sliderH, 2f,
                blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha)
            )
        }

        // 两个把手
        drawHandle(lowX, sliderX, sliderW, sliderY, sliderH, visAlpha)
        drawHandle(highX, sliderX, sliderW, sliderY, sliderH, visAlpha)

        if (dragState != 0) {
            applyDrag(mouseX)
        }
    }

    /** 根据当前鼠标位置推进拖拽，处理「方向未定」与「越过把手自动切换」 */
    private fun applyDrag(mouseX: Int) {
        val raw = valueAtMouse(mouseX)
        var state = dragState

        if (state == 1) {
            // 等距按下：按首个移动方向决定拖哪个把手
            state = when {
                mouseX > anchorX + 1 -> 3
                mouseX < anchorX - 1 -> 2
                else -> return
            }
            dragState = state
        }

        when (state) {
            2 -> // 下界越过当前上界 → 改为拖上界
                if (raw > value.high) {
                    dragState = 3
                    value.setHigh(raw)
                } else {
                    value.setLow(raw)
                }
            3 -> // 上界越过当前下界 → 改为拖下界
                if (raw < value.low) {
                    dragState = 2
                    value.setLow(raw)
                } else {
                    value.setHigh(raw)
                }
        }
    }

    private fun drawHandle(centerX: Float, sliderX: Float, sliderW: Float, sliderY: Float, sliderH: Float, visAlpha: Float) {
        val handleSize = 8f
        var handleX = centerX - handleSize / 2f
        handleX = max(sliderX - handleSize / 2f, min(handleX, sliderX + sliderW - handleSize / 2f))
        drawRound(
            handleX, sliderY - (handleSize - sliderH) / 2f, handleSize, handleSize, handleSize / 2f,
            blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha)
        )
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        val sliderY = y + 17
        if (!isHovering(mx, my, x, sliderY - 4, width, 10f) || button != 0) return

        val lowX = x + pct(value.low) * width
        val highX = x + pct(value.high) * width
        val newVal = valueAtMouse(mx)

        // 严格比较距离：明显靠近某把手时才直接选中
        val closerToLow = abs(mx - lowX) < abs(mx - highX)
        val closerToHigh = abs(mx - highX) < abs(mx - lowX)
        anchorX = mx
        when {
            closerToLow -> {
                dragState = 2
                value.setLow(newVal)
            }
            closerToHigh -> {
                dragState = 3
                value.setHigh(newVal)
            }
            else ->
                // 两把手重合：等首个移动方向再决定，避免死锁
                dragState = 1
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {
        if (button == 0) {
            dragState = 0
        }
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}

    override fun getHeight(): Float = 26f

    override fun visible(): Boolean = value.isVisible

    private fun blendAlpha(c: Color, a: Float): Color =
        Color(c.red, c.green, c.blue, (a * 255).toInt())
}
