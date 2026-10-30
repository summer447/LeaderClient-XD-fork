package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.module.Module
import unfair.property.PropertyManager
import unfair.property.properties.*
import unfair.ui.clickgui.oldgui.Component
import unfair.ui.clickgui.oldgui.dataset.impl.FloatSlider
import unfair.ui.clickgui.oldgui.dataset.impl.IntSlider
import unfair.ui.clickgui.oldgui.dataset.impl.PercentageSlider
import unfair.util.RenderUtil
import unfair.util.Timer
import java.awt.Color
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class ModuleComponent(var mod: Module, @JvmField var category: CategoryComponent, @JvmField var yPos: Int) : Component {
    val settings: ArrayList<Component> = ArrayList<Component>()
    private val enabledColor = Color(24, 154, 255).rgb
    private val disabledColor = Color(192, 192, 192).rgb
    @JvmField
    var isOpened: Boolean = false
    private val originalHoverAlpha = 120
    private val hoverColor = (Color(0, 0, 0, originalHoverAlpha)).rgb
    private var hovering = false
    private var hoverTimer: Timer? = null
    private var hoverStarted = false
    private var smoothTimer: Timer? = null
    private var smoothingY = 16
    private var targetHeight = 16
    private var isAnimatingHeight = false

    init {
        var y = yPos + 12
        if (!Objects.requireNonNull<PropertyManager>(Unfair.propertyManager).properties[mod.javaClass]!!.isEmpty()
        ) {
            for (baseProperty in Unfair.propertyManager!!.properties[mod.javaClass]!!) {
                when (baseProperty) {
                    is BooleanProperty -> {
                        val property = baseProperty
                        val c = CheckBoxComponent(property, this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is RangeProperty -> {
                        val c = RangeSliderComponent(baseProperty, this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is FloatProperty -> {
                        val property = baseProperty
                        val c = SliderComponent(FloatSlider(property), this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is IntProperty -> {
                        val property = baseProperty
                        val c = SliderComponent(IntSlider(property), this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is PercentProperty -> {
                        val property = baseProperty
                        val c = SliderComponent(PercentageSlider(property), this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is ModeProperty -> {
                        val property = baseProperty
                        val c = ModeComponent(property, this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is ColorProperty -> {
                        val property = baseProperty
                        val c = ColorSliderComponent(property, this, y)
                        this.settings.add(c)
                        y += c.height
                    }

                    is TextProperty -> {
                        val property = baseProperty
                        val c = TextComponent(property, this, y)
                        this.settings.add(c)
                        y += c.height
                    }
                }
            }
        }

        this.settings.add(BindComponent(this, y))
    }

    override fun updateHeight(newY: Int) {
        this.yPos = newY
        var y = this.yPos + 16
        val var3: MutableIterator<*> = this.settings.iterator()

        while (true) {
            while (var3.hasNext()) {
                val co = var3.next() as Component
                if (!isVisible(co)) {
                    continue
                }
                co.updateHeight(y)
                y += co.getHeight()
            }

            return
        }
    }

    override fun render() {
        if (hovering || hoverTimer != null) {
            val hoverAlpha = (if (hovering && hoverTimer != null) hoverTimer!!.getValueFloat(
                0f,
                originalHoverAlpha.toFloat(),
                1
            ) else if (hoverTimer != null) originalHoverAlpha - hoverTimer!!.getValueFloat(
                0f,
                originalHoverAlpha.toFloat(),
                1
            ) else originalHoverAlpha.toFloat()).toDouble()
            if (hoverAlpha == 0.0) {
                hoverTimer = null
            }
            RenderUtil.drawRoundedRectangle(
                this.category.getX().toFloat(),
                (this.category.getY() + yPos).toFloat(),
                (this.category.getX() + this.category.width).toFloat(),
                (this.category.getY() + 16 + this.yPos).toFloat(),
                8f,
                mergeAlpha(hoverColor, hoverAlpha.toInt())
            )
        }
        val button_rgb = if (this.mod.isEnabled) enabledColor else disabledColor

        if (smoothTimer != null && System.currentTimeMillis() - smoothTimer!!.last >= 300) {
            smoothTimer = null
            isAnimatingHeight = false
        }
        if (smoothTimer != null) {
            if (isAnimatingHeight) {
                // Height change animation (for mode switches)
                smoothingY = if (targetHeight > smoothingY) {
                    smoothTimer!!.getValueInt(smoothingY, targetHeight, 1)
                } else {
                    smoothTimer!!.getValueInt(targetHeight, smoothingY, 1)
                }
                if (smoothingY == targetHeight) {
                    smoothTimer = null
                    isAnimatingHeight = false
                }
            } else if (isOpened) {
                smoothingY = smoothTimer!!.getValueInt(16, this.moduleHeight, 1)
                if (smoothingY == this.moduleHeight) {
                    smoothTimer = null
                }
            } else {
                smoothingY = smoothTimer!!.getValueInt(this.moduleHeight, 16, 1)
                if (smoothingY == 16) {
                    smoothTimer = null
                }
            }
            this.category.updateHeight()
        }

        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            this.mod.getName(),
            (this.category.getX() + this.category.width / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(
                this.mod.getName()
            ) / 2).toFloat(),
            (this.category.getY() + this.yPos + 4).toFloat(),
            button_rgb
        )
        val scissorRequired = smoothTimer != null
        if (scissorRequired) {
            GL11.glPushMatrix()
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            RenderUtil.scissor(
                (this.category.getX() - 2).toDouble(),
                (this.category.getY() + this.yPos + 4).toDouble(),
                (this.category.width + 4).toDouble(),
                (smoothingY + 4).toDouble()
            )
        }

        if (this.isOpened || smoothTimer != null) {
            for (settingComponent in this.settings) {
                if (!isVisible(settingComponent)) {
                    continue
                }
                settingComponent.render()
            }
        }

        if (scissorRequired) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glPopMatrix()
        }
    }

    override fun getHeight(): Int {
        if (smoothTimer != null) {
            return smoothingY
        }
        if (!this.isOpened) {
            return 16
        } else {
            var h = 16
            val var2: MutableIterator<*> = this.settings.iterator()

            while (true) {
                while (var2.hasNext()) {
                    val c = var2.next() as Component
                    if (!isVisible(c)) {
                        continue
                    }
                    h += c.getHeight()
                }

                return h
            }
        }
    }

    fun startHeightAnimation(fromHeight: Int, toHeight: Int) {
        this.smoothingY = fromHeight
        this.targetHeight = toHeight
        this.isAnimatingHeight = true
        (Timer(200f).also { this.smoothTimer = it }).start()
        this.category.updateHeight()
    }

    fun onSliderChange() {
        for (c in this.settings) {
            if (c is SliderComponent) {
                c.onSliderChange()
            }
        }
    }

    val moduleHeight: Int
        get() {
            var h = 16
            val var2: MutableIterator<*> = this.settings.iterator()

            while (true) {
                while (var2.hasNext()) {
                    val c = var2.next() as Component
                    if (!isVisible(c)) {
                        continue
                    }
                    h += c.getHeight()
                }

                return h
            }
        }

    override fun drawScreen(x: Int, y: Int) {
        for (c in this.settings) {
            c.drawScreen(x, y)
        }
        if (overModuleName(x, y) && this.category.opened) {
            hovering = true
            if (hoverTimer == null) {
                (Timer(75f).also { hoverTimer = it }).start()
                hoverStarted = true
            }
        } else {
            if (hovering && hoverStarted) {
                (Timer(75f).also { hoverTimer = it }).start()
            }
            hoverStarted = false
            hovering = false
        }
    }

    val name: String?
        get() = mod.getName()

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (this.overModuleName(x, y) && mouse == 0) {
            this.mod.toggle()
        }

        if (this.overModuleName(x, y) && mouse == 1) {
            this.isOpened = !this.isOpened
            (Timer(200f).also { this.smoothTimer = it }).start()
            this.category.updateHeight()
        }

        for (settingComponent in this.settings) {
            settingComponent.onClick(x, y, mouse)
        }
    }

    override fun mouseReleased(x: Int, y: Int, m: Int) {
        for (c in this.settings) {
            c.mouseReleased(x, y, m)
        }
    }

    override fun keyTyped(t: Char, k: Int) {
        for (c in this.settings) {
            c.keyTyped(t, k)
        }
    }

    override fun onScroll(scroll: Int) {
        for (component in this.settings) {
            component.onScroll(scroll)
        }
    }

    override fun onGuiClosed() {
        for (c in this.settings) {
            c.onGuiClosed()
        }
        smoothTimer = null
        hoverTimer = null
        smoothingY = getHeight()
    }

    fun overModuleName(x: Int, y: Int): Boolean {
        return x > this.category.getX() && x < this.category.getX() + this.category.width && y > this.category.moduleY + this.yPos && y < this.category.moduleY + 16 + this.yPos
    }

    fun isVisible(component: Component): Boolean {
        return component.isVisible()
    }

    private fun mergeAlpha(color: Int, alpha: Int): Int {
        val newAlpha = (alpha and 0xFF) shl 24
        return (color and 0x00FFFFFF) or newAlpha
    }

    override fun isVisible(): Boolean {
        return true
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        this.yPos = newOffsetY
        var y = this.yPos + 16

        for (c in this.settings) {
            c.setComponentStartAt(y)
            if (c.isVisible()) {
                y += c.getHeight()
            }
        }
    }

    override fun draw(offset: AtomicInteger?) {
    }

    override fun update(mousePosX: Int, mousePosY: Int) {
    }

    override fun mouseDown(x: Int, y: Int, button: Int) {
    }

//    fun getModuleHeight(): Int {
//        var h = 16
//        val var2: MutableIterator<*> = this.settings.iterator()
//
//        while (true) {
//            while (var2.hasNext()) {
//                val c = var2.next() as Component
//                if (!isVisible(c)) {
//                    continue
//                }
//                h += c.getHeight()
//            }
//
//            return h
//        }
//    }
}