package unfair.ui.clickgui.panel.settings

import org.lwjgl.opengl.GL11
import unfair.Unfair
import unfair.management.ClientSettings
import unfair.property.properties.ModeProperty
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.RenderUtil
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ModeSetting(private val value: ModeProperty) : PanelValueItem() {
    private var expandAnim = 0f
    private var expanded = false
    private var hoverAnim = 0f
    private var textAlpha = 0f
    private var modeScrollOffset = 0f
    private var targetScrollOffset = 0f
    private var tipAlpha = 0f
    @JvmField
    var panelScreenX: Float = 0f
    @JvmField
    var panelScreenY: Float = 0f
    @JvmField
    var col: Int = 0

    fun isExpanded(): Boolean {
        return expanded || expandAnim > 0.01f
    }

    fun setExpanded(expanded: Boolean) {
        this.expanded = expanded
        if (!expanded) {
            targetScrollOffset = 0f
            modeScrollOffset = 0f
        }
    }

    fun isHoveringExpanded(mx: Int, my: Int): Boolean {
        if (!isExpanded()) return false
        val modes = value.modes
        val selectedText = value.getModeString()
        val textW = Unfair.fontManager!!.getFont(14).getStringWidth(selectedText).toFloat()
        val btnW = max(textW + 16, 48f)
        val btnH = 18f
        val itemW = 48f
        val visibleCount = min(modes.size, MAX_VISIBLE_MODES)
        val currentExpandW = visibleCount * itemW * expandAnim
        val totalW = btnW + currentExpandW
        val panelX = x + width - totalW
        val panelY = y
        return mx >= panelX && mx <= panelX + totalW && my >= panelY && my <= panelY + btnH
    }

    fun handleScroll(delta: Int) {
        val modes = value.modes
        if (modes.size <= MAX_VISIBLE_MODES) return
        val itemW = 48f
        val step = itemW * 0.5f
        val maxScrollOffset: Float = (modes.size - MAX_VISIBLE_MODES) * itemW
        if (delta > 0) {
            targetScrollOffset -= step
        } else {
            targetScrollOffset += step
        }
        if (targetScrollOffset < 0) targetScrollOffset = 0f
        if (targetScrollOffset > maxScrollOffset) targetScrollOffset = maxScrollOffset
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(visible())
        val target = if (expanded) 1f else 0f
        expandAnim = lerp(expandAnim, target, 0.18f, deltaTime)

        if (expanded && expandAnim > 0.95f) {
            textAlpha = lerp(textAlpha, 1f, 0.2f, deltaTime)
        } else if (!expanded && expandAnim < 0.05f) {
            textAlpha = lerp(textAlpha, 0f, 0.2f, deltaTime)
        } else if (expanded) {
            textAlpha = lerp(textAlpha, 1f, 0.18f, deltaTime)
        } else {
            textAlpha = lerp(textAlpha, 0f, 0.3f, deltaTime)
            if (textAlpha < 0.01f) textAlpha = 0f
        }

        if (!expanded && textAlpha > 0.01f) {
            expandAnim = 1f
        }

        modeScrollOffset = lerp(modeScrollOffset, targetScrollOffset, 0.25f, deltaTime)
        if (abs(modeScrollOffset - targetScrollOffset) < 0.01f) {
            modeScrollOffset = targetScrollOffset
        }

        val scrollable = value.modes.size > MAX_VISIBLE_MODES && expanded && expandAnim > 0.5f
        val tipTarget = if (scrollable) 1f else 0f
        tipAlpha = lerp(tipAlpha, tipTarget, 0.15f, deltaTime)
    }

    private var isHoveringCurrent = false

    fun setHovering(hovering: Boolean) {
        isHoveringCurrent = hovering
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        Unfair.fontManager!!.getFont(16).drawString(
            value.name, x, y + 1,
            blendAlpha(ClientSettings.INSTANCE.settingNameColor, alpha * visAlpha).rgb, false
        )

        val modes = value.modes
        if (modes.size == 0) return

        val selectedText = value.getModeString()
        val font = 14
        val textW = Unfair.fontManager!!.getFont(font).getStringWidth(selectedText).toFloat()
        val btnW = max(textW + 16, 48f)
        val btnH = 18f
        val itemW = 48f
        val visibleCount = min(modes.size, MAX_VISIBLE_MODES)
        val currentExpandW = visibleCount * itemW * expandAnim

        val totalW = btnW + currentExpandW
        val panelY = y

        val panelX = x + width - totalW
        val btnX = panelX + currentExpandW

        val hovering = isHoveringCurrent
        val hoverTarget = if (hovering) 1f else 0f
        hoverAnim = lerp(hoverAnim, hoverTarget, 0.15f, 0.016f)

        val bgColor: Color = blendColor(
            ClientSettings.INSTANCE.modeBgNormalColor,
            ClientSettings.INSTANCE.modeBgHoverColor,
            hoverAnim
        )
        drawRound(panelX, panelY, totalW, btnH, 4f, blendAlpha(bgColor, alpha * visAlpha))

        if (currentExpandW > 1 && textAlpha > 0.01f) {
            val scrollOffset = modeScrollOffset
            val clipRight = min(btnX, x + width)

            val guiScale = ClientSettings.INSTANCE.guiScale
            val mcClipX = panelScreenX + (panelX - panelScreenX) * guiScale
            val mcClipY = panelScreenY + (panelY - panelScreenY) * guiScale
            val mcClipW = (clipRight - panelX) * guiScale
            val mcClipH = btnH * guiScale

            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT)
            if (mcClipW > 0 && mcClipH > 0) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
                RenderUtil.scissor(mcClipX.toDouble(), mcClipY.toDouble(), mcClipW.toDouble(), mcClipH.toDouble())
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
            }

            for (i in modes.indices) {
                val modeX = panelX + i * itemW - scrollOffset

                if (modeX + itemW < panelX || modeX >= clipRight) continue

                val selected = (i == value.value)
                val itemHover =
                    mouseX >= modeX && mouseX <= modeX + itemW && mouseY >= panelY && mouseY <= panelY + btnH

                if (selected || itemHover) {
                    val hlColor =
                        if (selected) ClientSettings.INSTANCE.modeItemSelectedColor else ClientSettings.INSTANCE.modeItemHoverColor
                    drawRound(
                        modeX + 2, panelY + 2, itemW - 4, btnH - 4, 3f,
                        blendAlpha(hlColor, alpha * textAlpha * visAlpha)
                    )
                }

                val tc =
                    if (selected) ClientSettings.INSTANCE.accentColor else ClientSettings.INSTANCE.modeTextNormalColor
                val modeText = modes[i]
                val modeTextW = Unfair.fontManager!!.getFont(13).getStringWidth(modeText).toFloat()
                val textPosX = modeX + (itemW - modeTextW) / 2f
                val textPosY = panelY + (btnH - 13) / 2f + 1

                Unfair.fontManager!!.getFont(13).drawString(
                    modeText, textPosX, textPosY,
                    blendAlpha(tc, alpha * textAlpha * visAlpha).rgb, false
                )
            }

            GL11.glPopAttrib()
        }

        if (tipAlpha > 0.01f) {
            val tipText = "Need more? Try to scroll."
            val tipW = (Unfair.fontManager!!.getFont(12).getStringWidth(tipText) + 16).toFloat()
            val tipH = 18f
            val tipX = panelX + (totalW - tipW) / 2f
            val tipY = panelY + btnH + 3

            drawRound(
                tipX, tipY, tipW, tipH, 3f,
                blendAlpha(ClientSettings.INSTANCE.tipBgColor, alpha * tipAlpha * visAlpha * 0.85f)
            )
            Unfair.fontManager!!.getFont(12).drawString(
                tipText,
                tipX + (tipW - Unfair.fontManager!!.getFont(12).getStringWidth(tipText)) / 2f,
                tipY + (tipH - 12) / 2f + 1.5f,
                blendAlpha(ClientSettings.INSTANCE.tipTextColor, alpha * tipAlpha * visAlpha).rgb, false
            )
        }

        Unfair.fontManager!!.getFont(14).drawString(
            selectedText,
            btnX + (btnW - textW) / 2f, panelY + (btnH - 14) / 2f + 1,
            blendAlpha(ClientSettings.INSTANCE.accentColor, alpha * visAlpha).rgb, false
        )
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button != 0) return
        val modes = value.modes
        val selectedText = value.getModeString()
        val textW = Unfair.fontManager!!.getFont(14).getStringWidth(selectedText).toFloat()
        val btnW = max(textW + 16, 48f)
        val btnH = 18f
        val itemW = 48f
        val visibleCount = min(modes.size, MAX_VISIBLE_MODES)
        val currentExpandW = visibleCount * itemW * expandAnim

        val totalW = btnW + currentExpandW
        val panelY = y

        val panelX = x + width - totalW
        val btnX = panelX + currentExpandW

        if (mx >= panelX && mx <= panelX + totalW && my >= panelY && my <= panelY + btnH) {
            val clickedBtn = (mx >= btnX && mx < btnX + btnW)

            if (expanded && expandAnim > 0.5f && !clickedBtn && mx >= panelX && mx < btnX) {
                val clickedIndex = ((mx - panelX + modeScrollOffset) / itemW).toInt()
                if (clickedIndex >= 0 && clickedIndex < modes.size) {
                    value.setValue(clickedIndex)
                    expanded = false
                    targetScrollOffset = 0f
                    modeScrollOffset = 0f
                    return
                }
            }

            if (clickedBtn) {
                expanded = !expanded
                if (!expanded) {
                    targetScrollOffset = 0f
                    modeScrollOffset = 0f
                }
            }
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {}
    override fun mouseDragged(mx: Int, my: Int, button: Int) {}
    override fun getHeight(): Float {
        return 24f
    }

    override fun visible(): Boolean {
        return value.isVisible
    }

    companion object {
        private const val MAX_VISIBLE_MODES = 4
        private fun blendColor(a: Color, b: Color, t: Float): Color {
            val r = (a.red + (b.red - a.red) * t).toInt()
            val g = (a.green + (b.green - a.green) * t).toInt()
            val bl = (a.blue + (b.blue - a.blue) * t).toInt()
            return Color(r, g, bl)
        }

        private fun blendAlpha(c: Color, alpha: Float): Color {
            return Color(c.red, c.green, c.blue, (alpha * 255).toInt())
        }
    }
}
