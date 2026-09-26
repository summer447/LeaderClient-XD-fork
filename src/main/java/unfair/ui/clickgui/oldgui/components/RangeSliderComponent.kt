package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.module.modules.render.HUD
import unfair.property.properties.RangeProperty
import unfair.ui.clickgui.oldgui.Component
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Old GUI 双滑块组件，对应 [RangeProperty]。
 */
class RangeSliderComponent(
    private val property: RangeProperty,
    private val parentModule: ModuleComponent,
    private var offsetY: Int
) : Component {
    private var x: Int
    private var y: Int
    // 0=未拖动 1=已按下但方向未定(两把手重合) 2=拖下界 3=拖上界
    private var dragState = 0
    private var anchorX = 0

    private val trackX: Int get() = parentModule.category.getX() + 4
    private val trackW: Int get() = parentModule.category.width - 8

    init {
        this.x = parentModule.category.getX()
        this.y = parentModule.category.getY() + parentModule.yPos
    }

    private fun frac(v: Float): Float {
        val span = property.maximum - property.minimum
        return if (span > 0) (v - property.minimum) / span else 0f
    }

    private fun valueAt(mousePosX: Int): Float {
        val d = min(trackW, max(0, mousePosX - this.x)).toDouble()
        var raw = d / trackW.toDouble() * (property.maximum - property.minimum) + property.minimum
        raw = Math.round(raw * 100.0) / 100.0
        return max(property.minimum, min(property.maximum, raw.toFloat()))
    }

    private fun lowPx(): Int = trackX + (frac(property.low) * trackW).toInt()
    private fun highPx(): Int = trackX + (frac(property.high) * trackW).toInt()

    private val valueText: String
        get() = "${Math.round(property.low * 100.0) / 100.0}-${Math.round(property.high * 100.0) / 100.0}"

    override fun draw(offset: AtomicInteger) {
        val top = parentModule.category.getY() + offsetY + 11
        val bottom = parentModule.category.getY() + offsetY + 15

        // 轨道
        Gui.drawRect(trackX, top, trackX + trackW, bottom, -12302777)

        val color = (Unfair.moduleManager!!.modules.get(HUD::class.java) as HUD)
            .getColor(System.currentTimeMillis(), offset.get().toLong()).rgb

        // 选中区间
        Gui.drawRect(lowPx(), top, highPx(), bottom, color)

        // 两个把手
        Gui.drawRect(lowPx() - 1, top - 2, lowPx() + 1, bottom + 2, Color(220, 220, 220).rgb)
        Gui.drawRect(highPx() - 1, top - 2, highPx() + 1, bottom + 2, Color(220, 220, 220).rgb)

        drawText(parentModule.category.getY(), top)
    }

    private fun drawText(baseY: Int, @Suppress("UNUSED_PARAMETER") top: Int) {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            property.name.replace("-", " ") + ": " + valueText,
            (trackX * 2).toFloat(),
            ((baseY + offsetY + 3) * 2).toFloat(),
            -1
        )
        GL11.glPopMatrix()
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.offsetY = newOffsetY
    }

    override fun getHeight(): Int = 16

    override fun update(mousePosX: Int, mousePosY: Int) {
        this.y = parentModule.category.getY() + offsetY
        this.x = parentModule.category.getX()
        applyDrag(mousePosX)
    }

    private fun applyDrag(mousePosX: Int) {
        val raw = valueAt(mousePosX)
        var state = dragState

        if (state == 1) {
            // 等距按下：按首个移动方向决定拖哪个把手
            state = when {
                mousePosX > anchorX + 1 -> 3
                mousePosX < anchorX - 1 -> 2
                else -> return
            }
            dragState = state
        }

        when (state) {
            2 -> // 下界越过当前上界 → 改为拖上界
                if (raw > property.high) {
                    dragState = 3
                    property.setHigh(raw)
                } else {
                    property.setLow(raw)
                }
            3 -> // 上界越过当前下界 → 改为拖下界
                if (raw < property.low) {
                    dragState = 2
                    property.setLow(raw)
                } else {
                    property.setHigh(raw)
                }
        }
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {}

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (mouse != 0 || !parentModule.isOpened) return
        if (x < trackX || x > trackX + trackW || y < this.y || y > this.y + 16) return

        // 严格比较距离：明显靠近某把手时才直接选中
        val closerToLow = abs(x - lowPx()) < abs(x - highPx())
        val closerToHigh = abs(x - highPx()) < abs(x - lowPx())
        anchorX = x
        when {
            closerToLow -> {
                dragState = 2
                property.setLow(valueAt(x))
            }
            closerToHigh -> {
                dragState = 3
                property.setHigh(valueAt(x))
            }
            else ->
                // 两把手重合：等首个移动方向再决定，避免死锁
                dragState = 1
        }
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
        if (button == 0) {
            dragState = 0
        }
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {}

    override fun isVisible(): Boolean = property.isVisible

    override fun render() {
        val top = parentModule.category.moduleY + offsetY + 11
        val bottom = parentModule.category.moduleY + offsetY + 15

        Gui.drawRect(trackX, top, trackX + trackW, bottom, -12302777)

        val color = Color.getHSBColor((System.currentTimeMillis() % 11000L).toFloat() / 11000.0f, 0.75f, 0.9f).rgb
        Gui.drawRect(lowPx(), top, highPx(), bottom, color)
        Gui.drawRect(lowPx() - 1, top - 2, lowPx() + 1, bottom + 2, Color(220, 220, 220).rgb)
        Gui.drawRect(highPx() - 1, top - 2, highPx() + 1, bottom + 2, Color(220, 220, 220).rgb)

        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            property.name.replace("-", " ") + ": " + valueText,
            (trackX * 2).toFloat(),
            ((parentModule.category.moduleY + offsetY + 3) * 2).toFloat(),
            -1
        )
        GL11.glPopMatrix()
    }

    override fun drawScreen(x: Int, y: Int) {
        this.y = parentModule.category.moduleY + offsetY
        this.x = parentModule.category.getX()
        applyDrag(x)
    }

    override fun updateHeight(y: Int) {
        this.offsetY = y
    }

    override fun onScroll(scroll: Int) {}

    override fun onGuiClosed() {}
}
