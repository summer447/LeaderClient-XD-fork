package unfair.ui.clickgui.panel.settings

import unfair.Unfair
import unfair.management.ClientSettings
import unfair.property.properties.ColorProperty
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.shader.RoundedUtils.drawGradientHorizontal
import unfair.util.shader.RoundedUtils.drawGradientVertical
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class ColorSetting(private val value: ColorProperty) : PanelValueItem() {
    private var expanded = false
    private var expandAnim = 0f
    private var contentAnim = 0f

    private var hue: Float
    private var saturation: Float
    private var brightness: Float

    private var draggingHue = false
    private var draggingSV = false

    var col: Int = 0

    init {
        val color = value.value
        val hsb = Color.RGBtoHSB((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, null)
        this.hue = hsb[0]
        this.saturation = hsb[1]
        this.brightness = hsb[2]
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(visible())
        if (expanded) {
            expandAnim = lerp(expandAnim, 1f, 0.18f, deltaTime)
            if (expandAnim > 0.95f) {
                contentAnim = lerp(contentAnim, 1f, 0.15f, deltaTime)
            }
        } else {
            contentAnim = lerp(contentAnim, 0f, 0.15f, deltaTime)
            if (contentAnim < 0.05f) {
                expandAnim = lerp(expandAnim, 0f, 0.18f, deltaTime)
            }
        }
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        Unfair.fontManager!!.getFont(16).drawString(
            value.name, x, y + 2,
            blendAlpha(ClientSettings.INSTANCE.settingNameColor, alpha * visAlpha).rgb, false
        )

        val currentColor = value.value and 0xFFFFFF
        val previewSize = 14f
        val previewX = x + width - previewSize
        val previewY = y + 2

        drawRound(
            previewX, previewY, previewSize, previewSize, 3f,
            blendAlpha(Color(currentColor), alpha * visAlpha)
        )

        if (expandAnim > 0.001f) {
            val pickerWidth = width - 4
            val pickerX = x + 2
            val svSize = pickerWidth - 16
            val svH = svSize * 0.7f
            val hueBarH = 10f
            val pickerContentH = 6 + svH + 8 + hueBarH + 6
            val pickerHeight = pickerContentH * expandAnim
            val pickerY = y + 24

            if (alpha * visAlpha > 0.01f) {
                drawRound(
                    pickerX, pickerY, pickerWidth, pickerHeight, 5f,
                    Color(
                        ClientSettings.INSTANCE.pickerBgColor.red,
                        ClientSettings.INSTANCE.pickerBgColor.green,
                        ClientSettings.INSTANCE.pickerBgColor.blue,
                        (alpha * visAlpha * 255).toInt()
                    )
                )

                val contentAlpha = alpha * contentAnim * visAlpha
                if (contentAlpha > 0.01f) {
                    val svX = pickerX + 8
                    val svY = pickerY + 6

                    val baseColor = Color.getHSBColor(hue, 1f, 1f)
                    drawRound(
                        svX,
                        svY,
                        svSize,
                        svH,
                        2f,
                        Color(
                            baseColor.red,
                            baseColor.green,
                            baseColor.blue,
                            (contentAlpha * 255).toInt()
                        )
                    )

                    val white = Color(255, 255, 255, (contentAlpha * 255).toInt())
                    val transparentWhite = Color(255, 255, 255, 0)
                    drawGradientHorizontal(svX, svY, svSize, svH, 2f, white, transparentWhite)

                    val black = Color(0, 0, 0, (contentAlpha * 255).toInt())
                    val transparentBlack = Color(0, 0, 0, 0)
                    drawGradientVertical(svX, svY, svSize, svH, 2f, transparentBlack, black)

                    val circleX = svX + saturation * svSize - 4
                    val circleY = svY + (1f - brightness) * svH - 4
                    val circleSize = 8f
                    val whiteOutline: Int = blendAlpha(Color(255, 255, 255), contentAlpha).rgb
                    val fillColor: Int =
                        blendAlpha(ClientSettings.INSTANCE.circleFillColor, contentAlpha * 0.667f).rgb
                    drawRound(circleX, circleY, circleSize, circleSize, circleSize / 2f, whiteOutline)
                    drawRound(
                        circleX + 1,
                        circleY + 1,
                        circleSize - 2,
                        circleSize - 2,
                        (circleSize - 2) / 2f,
                        fillColor
                    )

                    val hueBarY = svY + svH + 8
                    for (i in 0..<svSize.toInt()) {
                        val h = i.toFloat() / svSize
                        val c = Color.getHSBColor(h, 1f, 1f)
                        drawRound(
                            svX + i, hueBarY, 1f, hueBarH, 0f,
                            Color(c.red, c.green, c.blue, (contentAlpha * 255).toInt()).rgb
                        )
                    }

                    val hueX = svX + hue * svSize - 2
                    drawRound(hueX, hueBarY - 1, 4f, hueBarH + 2, 2f, whiteOutline)
                    drawRound(hueX + 1, hueBarY, 2f, hueBarH, 1f, fillColor)
                }
            }
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button != 0) return

        val previewSize = 14f
        val previewX = x + width - previewSize
        val previewY = y + 2

        if (mx >= previewX && mx <= previewX + previewSize && my >= previewY && my <= previewY + previewSize) {
            expanded = !expanded
            return
        }

        if (!expanded) return

        val pickerWidth = width - 4
        val svSize = pickerWidth - 16
        val svX = x + 2 + 8
        val svY = y + 24 + 6
        val svH = svSize * 0.7f

        if (mx >= svX && mx <= svX + svSize && my >= svY && my <= svY + svH) {
            draggingSV = true
            updateSV(mx, my)
            return
        }

        val hueBarY = svY + svH + 8
        val hueBarH = 10f
        if (mx >= svX && mx <= svX + svSize && my >= hueBarY && my <= hueBarY + hueBarH) {
            draggingHue = true
            updateHue(mx)
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {
        draggingHue = false
        draggingSV = false
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int) {
        if (button != 0) return

        if (draggingSV) {
            updateSV(mx, my)
        }
        if (draggingHue) {
            updateHue(mx)
        }
    }

    private fun updateSV(mx: Int, my: Int) {
        val pickerWidth = width - 4
        val svSize = pickerWidth - 16
        val svX = x + 2 + 8
        val svY = y + 24 + 6
        val svH = svSize * 0.7f

        saturation = max(0f, min(1f, (mx - svX) / svSize))
        brightness = max(0f, min(1f, 1f - (my - svY) / svH))

        applyColor()
    }

    private fun updateHue(mx: Int) {
        val pickerWidth = width - 4
        val svSize = pickerWidth - 16
        val svX = x + 2 + 8

        hue = max(0f, min(1f, (mx - svX) / svSize))
        applyColor()
    }

    private fun applyColor() {
        val rgb = Color.getHSBColor(hue, saturation, brightness)
        val color = (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
        value.setValue(color)
    }

    fun closePicker() {
        expanded = false
    }

    override fun getHeight(): Float {
        if (!expanded && expandAnim < 0.01f) return 22f
        val pickerWidth = width - 4
        val svSize = pickerWidth - 16
        val svH = svSize * 0.7f
        val pickerContentH = 6 + svH + 8 + 10 + 6
        return 22 + pickerContentH * expandAnim
    }

    override fun visible(): Boolean {
        return value.isVisible
    }

    companion object {
        private fun blendAlpha(c: Color, alpha: Float): Color {
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt())
        }
    }
}
