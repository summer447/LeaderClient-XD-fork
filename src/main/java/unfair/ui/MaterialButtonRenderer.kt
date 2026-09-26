package unfair.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import org.lwjgl.input.Mouse
import unfair.util.shader.RoundedUtils
import java.util.WeakHashMap
import kotlin.math.exp

/**
 * Shared Material You filled-button renderer, used by both [LeaderButton] and
 * MixinGuiButton (vanilla buttons). Keeps per-button hover/press animation state
 * so vanilla buttons animate the same way as LeaderButton.
 *
 * Vanilla draw order is preserved: container -> mouseDragged (slider thumb) -> label.
 */
object MaterialButtonRenderer {

    private class AnimState {
        var hover = 0f
        var press = 0f
        var lastTime: Long = System.currentTimeMillis()
    }

    private val states = WeakHashMap<GuiButton, AnimState>()

    /**
     * Draws the M3 container (skipped for sliders) and returns the hovered state.
     */
    @JvmStatic
    fun drawContainer(button: GuiButton, mc: Minecraft, mouseX: Int, mouseY: Int): Boolean {
        if (!button.visible) return false
        val isHovered =
            mouseX >= button.xPosition && mouseY >= button.yPosition && mouseX < button.xPosition + button.width && mouseY < button.yPosition + button.height

        val scheme = MaterialYou.scheme
        val state = states.getOrPut(button) { AnimState() }
        val now = System.currentTimeMillis()
        val delta = (now - state.lastTime).coerceAtMost(50L) / 1000f
        state.lastTime = now
        val blend = 1f - exp(-delta / 0.15f * 5f)
        val hoverTarget = if (isHovered && button.enabled) 1f else 0f
        val pressTarget = if (isHovered && button.enabled && Mouse.isButtonDown(0)) 1f else 0f
        state.hover += (hoverTarget - state.hover) * blend
        state.press += (pressTarget - state.press) * blend

        val containerColor: Int
        if (button.enabled) {
            containerColor = MaterialYou.applyStateLayer(
                MaterialYou.applyStateLayer(scheme.primary, scheme.onPrimary, state.hover * MaterialYou.STATE_HOVER),
                scheme.onPrimary, state.press * MaterialYou.STATE_PRESS
            )
        } else {
            containerColor = MaterialYou.withAlphaFraction(scheme.onSurface, MaterialYou.DISABLED_CONTAINER_ALPHA)
        }

        RoundedUtils.drawRound(
            button.xPosition.toFloat(), button.yPosition.toFloat(),
            button.width.toFloat(), button.height.toFloat(),
            button.height / 2f, containerColor
        )
        return isHovered
    }

    @JvmStatic
    fun drawLabel(button: GuiButton, mc: Minecraft) {
        if (!button.visible) return
        val scheme = MaterialYou.scheme
        val contentColor = when {
            button.enabled -> scheme.onPrimary
            else -> MaterialYou.withAlphaFraction(scheme.onSurface, MaterialYou.DISABLED_CONTENT_ALPHA)
        }
        val fr = mc.fontRendererObj
        fr.drawString(
            button.displayString,
            button.xPosition + (button.width - fr.getStringWidth(button.displayString)) / 2f,
            button.yPosition + (button.height - 8) / 2f,
            contentColor,
            false
        )
    }

    /**
     * Combined container + label (no mouseDragged hook), used by [LeaderButton].
     * Returns the hovered state.
     */
    @JvmStatic
    fun draw(button: GuiButton, mc: Minecraft, mouseX: Int, mouseY: Int): Boolean {
        val hovered = drawContainer(button, mc, mouseX, mouseY)
        drawLabel(button, mc)
        return hovered
    }
}
