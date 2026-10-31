package unfair.ui.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import unfair.management.altmanager.AltManagerGui
import unfair.ui.LeaderButton
import unfair.ui.MaterialYou
import java.io.IOException

class GuiMainMenuClient : GuiMainMenu() {
    override fun initGui() {
        super.initGui()
        this.buttonList.removeIf { button: GuiButton? -> button!!.id == 5 }
        for (i in this.buttonList.indices) {
            val button = this.buttonList[i]
            var newId = button.id
            var newText = button.displayString
            if (button.id == 14) {
                newId = 9999
                newText = "Alt Manager"
            }
            val leaderButton = LeaderButton(
                newId,
                button.xPosition,
                button.yPosition,
                button.width,
                button.height,
                newText
            )
            this.buttonList[i] = leaderButton
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawRect(0, 0, this.width, this.height, /*MaterialYou.scheme.surfaceContainerLow*/MaterialYou.scheme.surfaceContainer)
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        this.mc.textureManager.bindTexture(TITLE_TEXTURE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        val displayWidth = 400
        val displayHeight = (469.0 / 1563 * displayWidth).toInt()
        val titleX = (this.width - displayWidth) / 2 + 50
        val titleY = this.height / 5 - 20
        drawScaledCustomSizeModalRect(
            titleX, titleY,
            0f, 0f,
            1563, 469,
            displayWidth, displayHeight,
            1563f, 469f
        )
        GlStateManager.disableBlend()
        for (button in this.buttonList) {
            button.drawButton(this.mc, mouseX, mouseY)
        }
        this.fontRendererObj.drawString("Minecraft 1.8.9 Leader-XD Meow!!", 2, this.height - 10, MaterialYou.scheme.onSurface)
        this.fontRendererObj.drawString(
            "Copyright Mojang AB. Do not distribute!",
            this.width - this.fontRendererObj.getStringWidth("Copyright Mojang AB. Do not distribute!") - 2,
            this.height - 10, MaterialYou.scheme.onSurface
        )
    }

    @Throws(IOException::class)
    override fun actionPerformed(button: GuiButton) {
        if (button.id == 9999) {
            Minecraft.getMinecraft().displayGuiScreen(AltManagerGui())
            return
        }
        if (button.id == 1) {
            Minecraft.getMinecraft().displayGuiScreen(GuiSinglePlayerClient(this))
            return
        }
        if (button.id == 2) {
            Minecraft.getMinecraft().displayGuiScreen(GuiMultiPlayerClient(this))
            return
        }
        super.actionPerformed(button)
    }

    @Throws(IOException::class)
    public override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    companion object {
        private val TITLE_TEXTURE = ResourceLocation("minecraft", "unfair/texture/title.png")
    }
}