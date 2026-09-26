package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.module.modules.render.HUD
import unfair.ui.clickgui.oldgui.Component
import unfair.ui.clickgui.oldgui.dataset.Slider
import unfair.util.RenderUtil
import java.awt.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class SliderComponent(private val slider: Slider, private val parentModule: ModuleComponent, private var offsetY: Int) :
    Component {
    private var x: Int
    private var y: Int
    private var dragging = false
    private var sliderWidth = 0.0

    init {
        this.x = parentModule.category.getX() + parentModule.category.width
        this.y = parentModule.category.getY() + parentModule.yPos
    }

    override fun draw(offset: AtomicInteger) {
        Gui.drawRect(
            this.parentModule.category.getX() + 4,
            this.parentModule.category.getY() + this.offsetY + 11,
            this.parentModule.category.getX() + 4 + this.parentModule.category.width - 8,
            this.parentModule.category.getY() + this.offsetY + 15,
            -12302777
        )
        val sliderStart = this.parentModule.category.getX() + 4
        var sliderEnd = this.parentModule.category.getX() + 4 + this.sliderWidth.toInt()
        if (sliderEnd - sliderStart > 84) {
            sliderEnd = sliderStart + 84
        }
        Gui.drawRect(
            sliderStart,
            this.parentModule.category.getY() + this.offsetY + 11,
            sliderEnd,
            this.parentModule.category.getY() + this.offsetY + 15,
            (Unfair.moduleManager!!.modules.get(HUD::class.java) as HUD).getColor(
                System.currentTimeMillis(), offset.get().toLong()
            ).rgb
        )
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            this.slider.getName() + ": " + this.slider.getValueString(),
            (((this.parentModule.category.getX() + 4).toFloat() * 2.0f).toInt()).toFloat(),
            (((this.parentModule.category.getY() + this.offsetY + 3).toFloat() * 2.0f).toInt()).toFloat(),
            -1
        )
        GL11.glPopMatrix()
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.offsetY = newOffsetY
    }

    override fun getHeight(): Int {
        return 16
    }

    override fun update(mousePosX: Int, mousePosY: Int) {
        this.y = this.parentModule.category.getY() + this.offsetY
        this.x = this.parentModule.category.getX()

        val d = min(this.parentModule.category.width - 8, max(0, mousePosX - this.x)).toDouble()
        this.sliderWidth = (this.parentModule.category.width - 8).toDouble() *
                (this.slider.getInput() - this.slider.getMin()) /
                (this.slider.getMax() - this.slider.getMin())

        if (this.dragging) {
            if (d == 0.0) {
                this.slider.setValue(this.slider.getMin())
            } else {
                var rawValue = (d / (this.parentModule.category.width - 8).toDouble()
                        * (this.slider.getMax() - this.slider.getMin())
                        + this.slider.getMin())

                val increment = this.slider.getIncrement()
                if (increment > 0) {
                    rawValue = Math.round(rawValue / increment) * increment
                }
                var n: Double = roundToPrecision(rawValue, 2)
                n = max(this.slider.getMin(), min(this.slider.getMax(), n))
                this.slider.setValue(n)
            }
        }
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {
    }

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if ((this.isLeftHalfHovered(x, y) || this.isRightHalfHovered(
                x,
                y
            )) && mouse == 0 && this.parentModule.isOpened
        ) {
            this.dragging = true
        }
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
        this.dragging = false
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {
    }

    fun isLeftHalfHovered(x: Int, y: Int): Boolean {
        return x > this.x && x < this.x + this.parentModule.category.width / 2 + 1 && y > this.y && y < this.y + 16
    }

    fun isRightHalfHovered(x: Int, y: Int): Boolean {
        return x > this.x + this.parentModule.category.width / 2 && x < this.x + this.parentModule.category.width && y > this.y && y < this.y + 16
    }


    override fun isVisible(): Boolean {
        return slider.isVisible()
    }

    fun onSliderChange() {
    }

    override fun render() {
        // Draw slider background
        RenderUtil.drawRoundedRectangle(
            (this.parentModule.category.getX() + 4).toFloat(),
            (this.parentModule.category.moduleY + this.offsetY + 11).toFloat(),
            (this.parentModule.category.getX() + 4 + this.parentModule.category.width - 8).toFloat(),
            (this.parentModule.category.moduleY + this.offsetY + 15).toFloat(), 4f, -12302777
        )

        // Draw slider fill
        val left = this.parentModule.category.getX() + 4
        var right = left + this.sliderWidth.toInt()
        if (right - left > 84) {
            right = left + 84
        }
        RenderUtil.drawRoundedRectangle(
            left.toFloat(),
            (this.parentModule.category.moduleY + this.offsetY + 11).toFloat(),
            right.toFloat(),
            (this.parentModule.category.moduleY + this.offsetY + 15).toFloat(), 4f,
            Color.getHSBColor((System.currentTimeMillis() % 11000L).toFloat() / 11000.0f, 0.75f, 0.9f).rgb
        )

        // Draw text
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            this.slider.getName() + ": " + this.slider.getValueString(),
            ((this.parentModule.category.getX() + 4) * 2).toFloat(),
            ((this.parentModule.category.moduleY + this.offsetY + 3) * 2).toFloat(), -1
        )
        GL11.glPopMatrix()
    }

    override fun drawScreen(x: Int, y: Int) {
        this.y = this.parentModule.category.moduleY + this.offsetY
        this.x = this.parentModule.category.getX()
        this.sliderWidth = (this.parentModule.category.width - 8).toDouble() *
                (this.slider.getInput() - this.slider.getMin()) /
                (this.slider.getMax() - this.slider.getMin())
        if (this.dragging) {
            val d = min(this.parentModule.category.width - 8, max(0, x - this.x)).toDouble()
            if (d == 0.0) {
                this.slider.setValue(this.slider.getMin())
            } else {
                var rawValue = (d / (this.parentModule.category.width - 8).toDouble()
                        * (this.slider.getMax() - this.slider.getMin())
                        + this.slider.getMin())
                val increment = this.slider.getIncrement()
                if (increment > 0) {
                    rawValue = Math.round(rawValue / increment) * increment
                }
                var n: Double = roundToPrecision(rawValue, 2)
                n = max(this.slider.getMin(), min(this.slider.getMax(), n))
                this.slider.setValue(n)
            }
        }
    }

    override fun updateHeight(y: Int) {
        this.offsetY = y
    }

    override fun onScroll(scroll: Int) {
    }

    override fun onGuiClosed() {
    }

    companion object {
        private fun roundToPrecision(v: Double, precision: Int): Double {
            if (precision < 0) {
                return 0.0
            } else {
                var bd = BigDecimal(v)
                bd = bd.setScale(precision, RoundingMode.HALF_UP)
                return bd.toDouble()
            }
        }
    }
}