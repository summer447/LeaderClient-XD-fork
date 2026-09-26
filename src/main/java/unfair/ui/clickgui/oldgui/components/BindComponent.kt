package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.module.modules.render.GuiModule
import unfair.module.modules.render.HUD
import unfair.ui.clickgui.oldgui.Component
import unfair.ui.clickgui.oldgui.dataset.BindStage
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger

class BindComponent(private val parentModule: ModuleComponent, private var offsetY: Int) : Component {
    var isBinding: Boolean = false
    private var x: Int
    private var y: Int

    init {
        this.x = parentModule.category.getX() + parentModule.category.width
        this.y = parentModule.category.getY() + parentModule.yPos
    }

    override fun draw(offset: AtomicInteger) {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        this.renderText(
            if (this.isBinding) BindStage.binding else BindStage.bind + ": " + Keyboard.getKeyName(this.parentModule.mod.getKey()),
            (Unfair.moduleManager!!.modules.get(HUD::class.java) as HUD).getColor(
                System.currentTimeMillis(), offset.get().toLong()
            ).rgb
        )
        GL11.glPopMatrix()
    }

    override fun update(mousePosX: Int, mousePosY: Int) {
        val h = this.isHovered(mousePosX, mousePosY)
        this.y = this.parentModule.category.getY() + this.offsetY
        this.x = this.parentModule.category.getX()
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {
    }

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (this.isVisible() && this.isHovered(x, y) && mouse == 0 && this.parentModule.isOpened) {
            this.isBinding = !this.isBinding
            isAnyBinding = this.isBinding
        }
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {
        if (this.isBinding) {
            if (keyCode == 1 || keyCode == 14) {
                if (this.parentModule.mod is GuiModule) {
                    this.parentModule.mod.setKey(54)
                } else {
                    this.parentModule.mod.setKey(0)
                }
            } else {
                this.parentModule.mod.setKey(keyCode)
            }

            this.isBinding = false
            isAnyBinding = false
        }
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.offsetY = newOffsetY
    }

    fun isHovered(x: Int, y: Int): Boolean {
        return x > this.x && x < this.x + this.parentModule.category.width && y > this.y - 1 && y < this.y + 12
    }

    override fun getHeight(): Int {
        return 12
    }

    override fun isVisible(): Boolean {
        return this.parentModule.mod.isBindable
    }

    private fun renderText(s: String?, color: Int) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            s,
            ((this.parentModule.category.getX() + 4) * 2).toFloat(),
            ((this.parentModule.category.getY() + this.offsetY + 3) * 2).toFloat(),
            color
        )
    }

    override fun render() {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        val text =
            if (this.isBinding) "Press a key..." else "Bind: " + Keyboard.getKeyName(this.parentModule.mod.getKey())
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            text,
            ((this.parentModule.category.getX() + 4) * 2).toFloat(),
            ((this.parentModule.category.moduleY + this.offsetY + 3) * 2).toFloat(),
            if (this.isBinding) Color(255, 100, 100).rgb else -1
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

    companion object {
        var isAnyBinding: Boolean = false
    }
}
