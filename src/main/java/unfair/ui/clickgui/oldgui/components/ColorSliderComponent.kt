package unfair.ui.clickgui.oldgui.components

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import org.lwjgl.opengl.GL11
import unfair.enums.ChatColors
import unfair.property.properties.ColorProperty
import unfair.ui.clickgui.oldgui.Component
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class ColorSliderComponent(
    private val property: ColorProperty,
    private val parentModule: ModuleComponent,
    private var offsetY: Int
) : Component {
    private var draggingHue = false
    private var draggingSat = false
    private var draggingBri = false
    private var hue = 0f
    private var saturation = 0f
    private var brightness = 0f

    init {
        initColorValues()
    }

    private fun initColorValues() {
        val c = Color(property.value)
        val hsb = Color.RGBtoHSB(c.red, c.green, c.blue, null)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
    }

    override fun draw(offset: AtomicInteger?) {
        val x = parentModule.category.getX() + 4
        val y = parentModule.category.getY() + offsetY
        val width = parentModule.category.width - 8

        drawLabel(x, y)
        updateColorValues()
        drawColorPreview(x, y, width)
        drawSliders(x, y, width)
    }

    private fun drawLabel(x: Int, y: Int) {
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        val text = property.name.replace("-", " ") + ": " + ChatColors.formatColor(property.formatValue())
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            text,
            (x * 2).toFloat(),
            ((parentModule.category.getY() + offsetY + 3) * 2).toFloat(),
            -1
        )
        GL11.glPopMatrix()
    }

    private fun updateColorValues() {
        val color = Color(property.value)
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        if (!draggingHue) hue = hsb[0]
        if (!draggingSat) saturation = hsb[1]
        if (!draggingBri) brightness = hsb[2]
    }

    private fun drawColorPreview(x: Int, y: Int, width: Int) {
        val previewColor = Color.HSBtoRGB(hue, saturation, brightness)
        Gui.drawRect(x + width - 12, y + 2, x + width - 6, y + 8, previewColor)
    }

    private fun drawSliders(x: Int, y: Int, width: Int) {
        val hueY = y + 10
        val satY = hueY + 6
        val briY = satY + 6

        drawHueSlider(x, hueY, width)
        drawSaturationSlider(x, satY, width)
        drawBrightnessSlider(x, briY, width)
    }

    private fun drawHueSlider(x: Int, y: Int, width: Int) {
        for (i in 0..<width) {
            val h = i.toFloat() / width
            Gui.drawRect(x + i, y, x + i + 1, y + 4, Color.HSBtoRGB(h, 1f, 1f))
        }
        drawSliderPointer(x, y, width, hue)
    }

    private fun drawSaturationSlider(x: Int, y: Int, width: Int) {
        for (i in 0..<width) {
            val s = i.toFloat() / width
            Gui.drawRect(x + i, y, x + i + 1, y + 4, Color.HSBtoRGB(hue, s, 1f))
        }
        drawSliderPointer(x, y, width, saturation)
    }

    private fun drawBrightnessSlider(x: Int, y: Int, width: Int) {
        for (i in 0..<width) {
            val b = i.toFloat() / width
            Gui.drawRect(x + i, y, x + i + 1, y + 4, Color.HSBtoRGB(hue, saturation, b))
        }
        drawSliderPointer(x, y, width, brightness)
    }

    private fun drawSliderPointer(x: Int, y: Int, width: Int, value: Float) {
        val posX = x + (width * max(0f, min(1f, value))).toInt()
        Gui.drawRect(posX - 1, y, posX, y + 4, Color(0, 0, 0, 200).rgb)
    }

    override fun update(mouseX: Int, mouseY: Int) {
        val baseX = parentModule.category.getX() + 4
        val width = parentModule.category.width - 8

        if (draggingHue) {
            hue = clampValue(mouseX, baseX, width)
            updateColor()
        }
        if (draggingSat) {
            saturation = clampValue(mouseX, baseX, width)
            updateColor()
        }
        if (draggingBri) {
            brightness = clampValue(mouseX, baseX, width)
            updateColor()
        }
    }

    private fun clampValue(mouseX: Int, baseX: Int, width: Int): Float {
        val value = (mouseX - baseX).toFloat() / width
        return max(0f, min(1f, value))
    }

    private fun updateColor() {
        val rgb = Color.HSBtoRGB(hue, saturation, brightness) and 0xFFFFFF
        property.setValue(rgb)
    }

    override fun onClick(x: Int, y: Int, mouse: Int) {
        if (mouse != 0 || !parentModule.isOpened) return
        val baseY = parentModule.category.moduleY + offsetY + 10
        if (isSliderHovered(x, y, baseY)) {
            draggingHue = true
        } else if (isSliderHovered(x, y, baseY + 6)) {
            draggingSat = true
        } else if (isSliderHovered(x, y, baseY + 12)) {
            draggingBri = true
        }
    }

    override fun mouseDown(mouseX: Int, mouseY: Int, button: Int) {
    }

    private fun isSliderHovered(mouseX: Int, mouseY: Int, sliderY: Int): Boolean {
        val startX = parentModule.category.getX() + 4
        val endX = startX + parentModule.category.width - 8
        return mouseX >= startX && mouseX <= endX && mouseY >= sliderY && mouseY <= sliderY + 4
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
        draggingHue = false
        draggingSat = false
        draggingBri = false
    }

    override fun isVisible(): Boolean {
        return property.isVisible
    }

    override fun keyTyped(chatTyped: Char, keyCode: Int) {
    }

    override fun setComponentStartAt(newOffsetY: Int) {
        offsetY = newOffsetY
    }

    override fun getHeight(): Int {
        return 28
    }

    override fun render() {
        val x = parentModule.category.getX() + 4
        val y = parentModule.category.moduleY + offsetY
        val width = parentModule.category.width - 8

        // Draw label
        GL11.glPushMatrix()
        GL11.glScaled(0.5, 0.5, 0.5)
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(
            property.name,
            (x * 2).toFloat(),
            ((y + 3) * 2).toFloat(),
            -1
        )
        GL11.glPopMatrix()

        // Update color values from property
        updateColorValues()

        // Draw color preview
        val previewColor = Color.HSBtoRGB(hue, saturation, brightness)
        Gui.drawRect(x + width - 12, y + 2, x + width - 6, y + 8, previewColor)

        // Draw sliders
        val hueY = y + 10
        val satY = hueY + 6
        val briY = satY + 6

        // Hue slider
        for (i in 0..<width) {
            val h = i.toFloat() / width
            Gui.drawRect(x + i, hueY, x + i + 1, hueY + 4, Color.HSBtoRGB(h, 1f, 1f))
        }
        Gui.drawRect(x + (hue * width).toInt() - 1, hueY - 1, x + (hue * width).toInt() + 2, hueY + 5, -1)

        // Saturation slider
        for (i in 0..<width) {
            val s = i.toFloat() / width
            Gui.drawRect(x + i, satY, x + i + 1, satY + 4, Color.HSBtoRGB(hue, s, brightness))
        }
        Gui.drawRect(x + (saturation * width).toInt() - 1, satY - 1, x + (saturation * width).toInt() + 2, satY + 5, -1)

        // Brightness slider
        for (i in 0..<width) {
            val b = i.toFloat() / width
            Gui.drawRect(x + i, briY, x + i + 1, briY + 4, Color.HSBtoRGB(hue, saturation, b))
        }
        Gui.drawRect(x + (brightness * width).toInt() - 1, briY - 1, x + (brightness * width).toInt() + 2, briY + 5, -1)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int) {
        val x = parentModule.category.getX() + 4
        val baseY = parentModule.category.moduleY + offsetY + 10
        val width = parentModule.category.width - 8

        if (draggingHue) {
            val newHue = max(0f, min(1f, (mouseX - x) / width.toFloat()))
            hue = newHue
            updateColor()
        } else if (draggingSat) {
            val newSat = max(0f, min(1f, (mouseX - x) / width.toFloat()))
            saturation = newSat
            updateColor()
        } else if (draggingBri) {
            val newBri = max(0f, min(1f, (mouseX - x) / width.toFloat()))
            brightness = newBri
            updateColor()
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
