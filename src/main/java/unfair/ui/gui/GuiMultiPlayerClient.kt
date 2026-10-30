package unfair.ui.gui

import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import unfair.ui.LeaderButton
import unfair.ui.MaterialYou
import java.io.IOException

class GuiMultiPlayerClient(parentScreen: GuiScreen?) : GuiMultiplayer(parentScreen) {
    private val parent = parentScreen

    override fun initGui() {
        super.initGui()
        for (i in this.buttonList.indices) {
            val button = this.buttonList[i]

            val leaderButton = LeaderButton(
                button.id,
                button.xPosition,
                button.yPosition,
                button.width,
                button.height,
                button.displayString
            )
            this.buttonList[i] = leaderButton
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, this.width, this.height, MaterialYou.scheme.surfaceContainer)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun drawDefaultBackground() {
    }

    @Throws(IOException::class)
    override fun actionPerformed(button: GuiButton?) {
        super.actionPerformed(button)
    }

    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(parent)
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    @Throws(IOException::class)
    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }
}
