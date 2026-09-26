package unfair.ui.clickgui.panel.settings

import unfair.Unfair
import unfair.management.ClientSettings
import unfair.property.properties.BooleanProperty
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.min

class BoolSetting(private val value: BooleanProperty) : PanelValueItem() {
    private var animProgress: Float

    init {
        this.animProgress = if (value.value) 1f else 0f
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(visible())
        val target = if (value.value) 1f else 0f
        animProgress = lerp(animProgress, target, 0.15f, deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        Unfair.fontManager!!.getFont(16).drawString(
            value.name, x, y + 2,
            blendAlpha(ClientSettings.INSTANCE.settingNameColor, alpha * visAlpha).rgb, false
        )

        val toggleW = 26f
        val toggleH = 12f
        val dotSize = toggleH - 4
        val padding = 2f
        val maxDotX = toggleW - dotSize - padding

        val toggleX = x + width - toggleW
        val toggleY = y + 2

        if (alpha < 0.01f) return

        if (animProgress < 0.5f) {
            drawRound(
                toggleX, toggleY, toggleW, toggleH, toggleH / 2f,
                blendAlpha(ClientSettings.INSTANCE.toggleOffColor, alpha * visAlpha * (1 - animProgress * 2))
            )
        }
        if (animProgress > 0.0f) {
            drawRound(
                toggleX, toggleY, toggleW, toggleH, toggleH / 2f,
                blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha * min(animProgress * 2, 1f))
            )
        }

        val dotX = toggleX + padding + animProgress * (maxDotX - padding)
        drawRound(
            dotX, toggleY + padding, dotSize, dotSize, dotSize / 2f,
            blendAlpha(ClientSettings.INSTANCE.cardColor, alpha * visAlpha)
        )
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button == 0 && isHovering(mx, my, x, y, width, height)) {
            value.setValue(!value.value)
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {}
    override fun mouseDragged(mx: Int, my: Int, button: Int) {}
    override fun getHeight(): Float {
        return 22f
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
