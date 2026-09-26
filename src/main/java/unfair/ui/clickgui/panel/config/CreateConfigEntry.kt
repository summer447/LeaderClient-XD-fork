package unfair.ui.clickgui.panel.config

import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Keyboard
import unfair.Unfair
import unfair.config.Config
import unfair.management.ClientSettings
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.ChatUtil.sendFormatted
import unfair.util.RenderUtil
import unfair.util.shader.RoundedUtils.drawRound
import unfair.util.shader.RoundedUtils.drawRoundedRectRise
import java.awt.Color
import java.io.File
import kotlin.math.sin

class CreateConfigEntry : PanelValueItem() {
    private var focused = false
    private var newName = ""
    private var cursorPos = 0

    private var focusAnim = 0f
    private var widthAnim = 0f
    private var textAnimProgress = 1f
    private var lastTextLength = 0

    private var successAnim = 0f
    private var errorAnim = 0f

    private var justCreated = false


    private val baseW_ratio = 0.85f
    private val expandedW_ratio = 1f

    init {
        initVisibility(true)
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(true)
        if (focused) {
            focusAnim = lerp(focusAnim, 1f, 0.15f, deltaTime)
            widthAnim = lerp(widthAnim, 1f, 0.15f, deltaTime)
        } else {
            focusAnim = lerp(focusAnim, 0f, 0.15f, deltaTime)
            widthAnim = lerp(widthAnim, 0f, 0.15f, deltaTime)
        }

        if (newName.length != lastTextLength) {
            textAnimProgress = 0f
            lastTextLength = newName.length
        }
        textAnimProgress = lerp(textAnimProgress, 1f, 0.25f, deltaTime)

        successAnim = lerp(successAnim, 0f, 0.1f, deltaTime)
        errorAnim = lerp(errorAnim, 0f, 0.1f, deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val h = height
        val btnW = 50f
        val inputH = 26f

        val baseInputW = (width - 70) * baseW_ratio
        val expandedInputW = (width - 70) * expandedW_ratio
        val currentInputW = baseInputW + (expandedInputW - baseInputW) * widthAnim

        val inputX = x
        val inputY = y

        val bgAlpha = (0.3f + 0.7f * widthAnim) * alpha
        val bgColor: Color = blendAlpha(ClientSettings.INSTANCE.inputBgColor, bgAlpha)

        if (focusAnim > 0.01f) {
            drawRoundedRectRise(
                inputX, inputY, currentInputW, inputH, 5f,
                bgColor.rgb, false, false, true, true
            )
        } else {
            drawRound(inputX, inputY, currentInputW, inputH, 5f, bgColor)
        }

        if (focusAnim > 0.001f && alpha > 0.01f) {
            val underlineAlpha = focusAnim * 0.6f
            val borderColor: Color = blendAlpha(ClientSettings.INSTANCE.inputBorder_color, underlineAlpha)

            val underlineStartX = inputX + currentInputW * (1f - widthAnim) / 2f
            val underlineEndX = inputX + currentInputW - currentInputW * (1f - widthAnim) / 2f

            if (widthAnim > 0.02f && underlineEndX > underlineStartX) {
                RenderUtil.drawRect(
                    underlineStartX.toDouble(),
                    (inputY + inputH - 1).toDouble(),
                    underlineEndX.toDouble(),
                    (inputY + inputH).toDouble(),
                    borderColor.rgb
                )
            }
        }

        if (newName.isEmpty() && !focused) {
            val placeholderAlpha = (0.5f + 0.5f * (1f - widthAnim)) * alpha
            val placeholderOffset = sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2).toFloat() * 0.5f
            Unfair.fontManager!!.getFont(16).drawString(
                "Config name...", inputX + 10, inputY + 6 + placeholderOffset,
                blendAlpha(ClientSettings.INSTANCE.inputPlaceholderColor, placeholderAlpha).rgb, false
            )
        } else {
            val textScale = 1f + (1f - textAnimProgress) * 0.05f
            val textAlpha = (0.7f + 0.3f * textAnimProgress) * alpha

            GlStateManager.pushMatrix()
            val textCenterX = inputX + 10 + Unfair.fontManager!!.getFont(16).getStringWidth(newName) / 2f
            val textCenterY = inputY + 6 + 8
            GlStateManager.translate(textCenterX, textCenterY, 0f)
            GlStateManager.scale(textScale, textScale, 1f)
            GlStateManager.translate(-textCenterX, -textCenterY, 0f)

            Unfair.fontManager!!.getFont(16).drawString(
                newName, inputX + 10, inputY + 6,
                blendAlpha(ClientSettings.INSTANCE.inputTextColor, focusAnim * textAlpha).rgb, false
            )

            GlStateManager.popMatrix()

            if (focused) {
                val beforeCursor = newName.substring(0, cursorPos)
                val cursorX = inputX + 10 + Unfair.fontManager!!.getFont(16).getStringWidth(beforeCursor)

                val cursorTime = System.currentTimeMillis()
                val cursorVisible = (cursorTime % 1000 < 500) ||
                        (cursorTime % 120 < 60 && textAnimProgress < 0.9f)

                if (cursorVisible || successAnim > 0.01f || errorAnim > 0.01f) {
                    val cursorAlpha =
                        if (textAnimProgress < 0.9f) 1f else (sin(cursorTime / 200.0 * Math.PI) * 0.3f + 0.7f).toFloat()

                    if (successAnim > 0.01f) {
                        RenderUtil.drawRect(
                            cursorX.toDouble(),
                            (inputY + 6).toDouble(),
                            (cursorX + 1).toDouble(),
                            (inputY + 22).toDouble(),
                            blendAlpha(ClientSettings.INSTANCE.successColor, alpha * cursorAlpha).rgb
                        )
                    } else if (errorAnim > 0.01f) {
                        RenderUtil.drawRect(
                            cursorX.toDouble(),
                            (inputY + 6).toDouble(),
                            (cursorX + 1).toDouble(),
                            (inputY + 22).toDouble(),
                            blendAlpha(ClientSettings.INSTANCE.errorColor, alpha * cursorAlpha).rgb
                        )
                    } else {
                        RenderUtil.drawRect(
                            cursorX.toDouble(),
                            (inputY + 6).toDouble(),
                            (cursorX + 1).toDouble(),
                            (inputY + 22).toDouble(),
                            blendAlpha(ClientSettings.INSTANCE.inputBorder_color, focusAnim * cursorAlpha).rgb
                        )

                        if (textAnimProgress < 0.95f) {
                            val glowW = 2f + (1f - textAnimProgress) * 3f
                            val glowAlpha = (1f - textAnimProgress) * 0.3f * alpha
                            RenderUtil.drawRect(
                                (cursorX - glowW / 2).toDouble(), (inputY + 6).toDouble(),
                                (cursorX + glowW / 2).toDouble(), (inputY + 22).toDouble(),
                                blendAlpha(ClientSettings.INSTANCE.inputBorder_color, glowAlpha).rgb
                            )
                        }
                    }
                }
            }
        }

        val btnX = inputX + currentInputW + 8
        val btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= inputY && mouseY <= inputY + inputH

        var btnColor = ClientSettings.INSTANCE.buttonPrimaryColor
        if (btnHovered) btnColor = btnColor.brighter()
        if (successAnim > 0.3f) btnColor = blendColor(btnColor, ClientSettings.INSTANCE.successColor, successAnim)

        drawRound(btnX, inputY, btnW, inputH, 5f, blendAlpha(btnColor, alpha))

        val text = "Create"
        val textW = Unfair.fontManager!!.getFont(16).getStringWidth(text).toFloat()
        Unfair.fontManager!!.getFont(16).drawString(
            text,
            btnX + (btnW - textW) / 2f, inputY + (inputH - 16) / 2f + 2.0f,
            blendAlpha(Color.WHITE, alpha).rgb, false
        )

        if (successAnim > 0.05f) {
            drawRound(
                x, y, width, h, 5f,
                blendAlpha(ClientSettings.INSTANCE.successColor, alpha * successAnim * 0.15f)
            )
        }
        if (errorAnim > 0.05f) {
            drawRound(
                x, y, width, h, 5f,
                blendAlpha(ClientSettings.INSTANCE.errorColor, alpha * errorAnim * 0.15f)
            )
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button != 0) return

        val baseInputW = (width - 70) * baseW_ratio
        val expandedInputW = (width - 70) * expandedW_ratio
        val currentInputW = baseInputW + (expandedInputW - baseInputW) * widthAnim

        val btnX = x + currentInputW + 8

        if (mx >= btnX && mx <= btnX + 50 && my >= y && my <= y + 26) {
            createConfig()
            return
        }

        if (mx >= x && mx <= x + currentInputW && my >= y && my <= y + 26) {
            focused = true
            cursorPos = newName.length
            return
        }

        focused = false
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {}

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}

    override fun getHeight(): Float {
        return 36f
    }

    override fun visible(): Boolean {
        return true
    }

    fun isFocused(): Boolean {
        return focused
    }

    fun justCreatedConfig(): Boolean {
        if (justCreated) {
            justCreated = false
            return true
        }
        return false
    }

    fun setFocused(f: Boolean) {
        focused = f
        if (focused) {
            cursorPos = newName.length
        }
    }

    fun handleKeyTyped(typedChar: Char, keyCode: Int) {
        if (!focused) return

        if (keyCode == Keyboard.KEY_RETURN) {
            createConfig()
            return
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            focused = false
            return
        }

        if (keyCode == Keyboard.KEY_BACK && cursorPos > 0) {
            newName = newName.substring(0, cursorPos - 1) + newName.substring(cursorPos)
            cursorPos--
            return
        }

        if (Character.isLetterOrDigit(typedChar) || typedChar == '_' || typedChar == '-') {
            newName = newName.substring(0, cursorPos) + typedChar + newName.substring(cursorPos)
            cursorPos++
        }
    }

    private fun createConfig() {
        if (newName.isEmpty()) {
            errorAnim = 1f
            sendFormatted(Unfair.clientName + "&cPlease enter a config name")
            return
        }

        try {
            val testFile = File("./config/Unfair/$newName.json")
            if (testFile.exists()) {
                errorAnim = 1f
                sendFormatted(Unfair.clientName + "&cConfig exists: &f" + newName)
                return
            }

            val config = Config(newName, true)
            config.save()

            successAnim = 1f
            justCreated = true
            sendFormatted(Unfair.clientName + "&aCreated: &f" + newName)

            newName = ""
            cursorPos = 0
            lastTextLength = 0
            textAnimProgress = 1f
            focused = false
        } catch (_: Exception) {
            errorAnim = 1f
            sendFormatted(Unfair.clientName + "&cFailed to create: &f" + newName)
        }
    }

    companion object {
        private fun blendColor(a: Color, b: Color, t: Float): Color {
            val r = (a.red + (b.red - a.red) * t).toInt()
            val g = (a.green + (b.green - a.green) * t).toInt()
            val bl = (a.blue + (b.blue - a.blue) * t).toInt()
            return Color(r, g, bl)
        }

        private fun blendAlpha(c: Color, a: Float): Color {
            return Color(c.red, c.green, c.blue, (a * 255).toInt())
        }
    }
}
