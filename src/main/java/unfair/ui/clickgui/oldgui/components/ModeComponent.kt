package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import unfair.enums.ChatColors
import unfair.property.properties.ModeProperty
import unfair.ui.clickgui.oldgui.Component
import java.awt.Color
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class ModeComponent(
    private val property: ModeProperty,
    private val parentModule: ModuleComponent,
    private var offsetY: Int
) : Component {
    private var x: Int
    private var y: Int

    init {
        this.x = parentModule.category.getX() + parentModule.category.width
        this.y = parentModule.category.getY() + parentModule.yPos
    }

    override fun draw(offset: AtomicInteger?) {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        var mode = this.property.getModeString()
        mode = mode.replace("_", " ")
        val bruhWidth =
            (Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.property.name + ": ") * 0.5).toInt()
        Minecraft.getMinecraft().fontRendererObj.drawString(
            this.property.name + ": ",
            ((this.parentModule.category.getX() + 4) * 2).toFloat(),
            ((this.parentModule.category.getY() + this.offsetY + 4) * 2).toFloat(),
            -0x1,
            true
        )
        Minecraft.getMinecraft().fontRendererObj.drawString(
            ChatColors.formatColor(
                "&9" + mode.substring(0, 1).uppercase(
                    Locale.getDefault()
                ) + mode.substring(1).lowercase(Locale.getDefault())
            ),
            ((this.parentModule.category.getX() + 4 + bruhWidth) * 2).toFloat(),
            ((this.parentModule.category.getY() + this.offsetY + 4) * 2).toFloat(),
            -1,
            true
        )
        GL11.glPopMatrix()
    }

    override fun update(mousePosX: Int, mousePosY: Int) {
        this.y = this.parentModule.category.moduleY + this.offsetY
        this.x = this.parentModule.category.getX()
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.offsetY = newOffsetY
    }

    override fun getHeight(): Int {
        return 12
    }

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (isHovered(x, y) && this.parentModule.isOpened) {
            val oldHeight = this.parentModule.moduleHeight
            if (mouse == 0) {
                this.property.nextMode()
            } else if (mouse == 1) {
                this.property.previousMode()
            }
            val newHeight = this.parentModule.moduleHeight
            // If height changed, trigger smooth animation
            if (oldHeight != newHeight) {
                this.parentModule.startHeightAnimation(oldHeight, newHeight)
            }
        }
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {
    }

    private fun isHovered(x: Int, y: Int): Boolean {
        return x > this.x && x < this.x + this.parentModule.category.width && y > this.y && y < this.y + 11
    }

    override fun isVisible(): Boolean {
        return property.isVisible
    }

    override fun render() {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        var mode = this.property.getModeString()
        mode = mode.replace("_", " ")
        val bruhWidth =
            (Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.property.name + ": ") * 0.5).toInt()
        Minecraft.getMinecraft().fontRendererObj.drawString(
            this.property.name + ": ",
            ((this.parentModule.category.getX() + 4) * 2).toFloat(),
            ((this.parentModule.category.moduleY + this.offsetY + 4) * 2).toFloat(), -0x1, true
        )
        Minecraft.getMinecraft().fontRendererObj.drawString(
            mode.substring(0, 1).uppercase(Locale.getDefault()) + mode.substring(1).lowercase(Locale.getDefault()),
            ((this.parentModule.category.getX() + 4 + bruhWidth) * 2).toFloat(),
            ((this.parentModule.category.moduleY + this.offsetY + 4) * 2).toFloat(),
            Color(100, 200, 255).rgb, true
        )
        GL11.glPopMatrix()
    }

    override fun drawScreen(x: Int, y: Int) {
        this.y = this.parentModule.category.moduleY + this.offsetY
        this.x = this.parentModule.category.getX()
    }

    override fun updateHeight(y: Int) {
        this.offsetY = y
    }

    override fun onScroll(scroll: Int) {
    }

    override fun onGuiClosed() {
    }
}
