package unfair.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton

/**
 * Material You filled button, ported from lite-material-components-4a
 * (dev1503.lmc4a.v3.widget.button.MaterialButton, ButtonStyle.FILLED).
 * Rendering is shared with vanilla buttons via [MaterialButtonRenderer].
 */
class LeaderButton(buttonId: Int, x: Int, y: Int, widthIn: Int, heightIn: Int, buttonText: String?) :
    GuiButton(buttonId, x, y, widthIn, heightIn, buttonText) {

    override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int) {
        this.hovered = MaterialButtonRenderer.draw(this, mc, mouseX, mouseY)
    }
}
