package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import unfair.enums.ChatColors
import unfair.property.properties.TextProperty
import unfair.ui.clickgui.oldgui.ClickGui
import unfair.ui.clickgui.oldgui.Component
import unfair.ui.clickgui.oldgui.callback.GuiInput.Companion.prompt
import java.util.concurrent.atomic.AtomicInteger

class TextComponent(private val property: TextProperty, private val module: ModuleComponent, private var offsetY: Int) :
    Component {
    private var x: Int
    private var y: Int

    init {
        this.x = module.category.getX() + module.category.width
        this.y = module.category.getY() + module.yPos
    }


    override fun draw(offset: AtomicInteger?) {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawString(
            this.property.name.replace("-", " ") + ": " + ChatColors.formatColor(this.property.formatValue()),
            ((this.module.category.getX() + 4) * 2).toFloat(),
            ((this.module.category.getY() + this.offsetY + 5) * 2).toFloat(),
            -1,
            false
        )
        GL11.glPopMatrix()
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.offsetY = newOffsetY
    }

    override fun getHeight(): Int {
        return 12
    }

    override fun update(mousePosX: Int, mousePosY: Int) {
        this.y = this.module.category.getY() + this.offsetY
        this.x = this.module.category.getX()
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {
    }

    fun isHovered(x: Int, y: Int): Boolean {
        return x > this.x && x < this.x + this.module.category.width && y > this.y && y < this.y + 11
    }


    override fun isVisible(): Boolean {
        return property.isVisible
    }

    override fun render() {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawString(
            this.property.name.replace("-", " ") + ": " + this.property.value,
            ((this.module.category.getX() + 4) * 2).toFloat(),
            ((this.module.category.moduleY + this.offsetY + 4) * 2).toFloat(), -1, false
        )
        GL11.glPopMatrix()
    }

    override fun drawScreen(x: Int, y: Int) {
        this.y = this.module.category.moduleY + this.offsetY
        this.x = this.module.category.getX()
    }

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (this.isHovered(x, y) && mouse == 0 && this.module.isOpened) {
            prompt(
                property.name.replace("-", " "),
                property.value,
                { `object`: String? -> property.setValue(`object`) },
                ClickGui.instance
            )
        }
    }

    override fun updateHeight(y: Int) {
        this.offsetY = y
    }

    override fun onScroll(scroll: Int) {
    }

    override fun onGuiClosed() {
    }
}