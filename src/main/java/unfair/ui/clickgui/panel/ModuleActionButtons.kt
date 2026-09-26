package unfair.ui.clickgui.panel

import org.lwjgl.input.Keyboard
import unfair.Unfair
import unfair.management.ClientSettings
import unfair.module.Module
import unfair.util.KeyBindUtil.getKeyName
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color

class ModuleActionButtons(private val module: Module) : PanelValueItem() {
    private val buttonWidth = 24f
    private val buttonHeight = 16f
    private val gap = 2f

    private var bindHovered = false
    private var hideHovered = false

    private var bindHoverAnim = 0f
    private var hideHoverAnim = 0f
    private var bindActiveAnim = 0f
    private var hideActiveAnim = 0f

    var isBinding: Boolean = false
        private set

    init {
        this.width = (buttonWidth + gap) * 2 - gap
        initVisibility(true)
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        setTargetVisibility(true)
        val targetBindAnim = if (bindHovered) 1f else 0f
        val targetHideAnim = if (hideHovered) 1f else 0f
        val targetBindActive = if (module.getKey() != 0) 1f else 0f
        val targetHideActive = if (module.isHidden) 1f else 0f

        bindHoverAnim = lerp(bindHoverAnim, targetBindAnim, 0.18f, deltaTime)
        hideHoverAnim = lerp(hideHoverAnim, targetHideAnim, 0.18f, deltaTime)
        bindActiveAnim = lerp(bindActiveAnim, targetBindActive, 0.18f, deltaTime)
        hideActiveAnim = lerp(hideActiveAnim, targetHideActive, 0.18f, deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        val visAlpha = visibilityAlpha
        if (visAlpha < 0.01f) return

        val bindable = module.isBindable
        val bindX = x
        val hideX = if (bindable) x + buttonWidth + gap else x
        val btnY = y

        bindHovered = bindable && isHovering(mouseX, mouseY, bindX, btnY, buttonWidth, buttonHeight)
        hideHovered = isHovering(mouseX, mouseY, hideX, btnY, buttonWidth, buttonHeight)

        val bgNormal = ClientSettings.INSTANCE.actionBtnNormalColor
        val bgHover = ClientSettings.INSTANCE.actionBtnHoverColor

        val bindBgColor: Color
        if (this.isBinding) {
            bindBgColor = ClientSettings.INSTANCE.bindActiveColor
        } else if (bindActiveAnim > 0.01f) {
            bindBgColor = blendColor(bgNormal, ClientSettings.INSTANCE.bindActiveBgColor, bindActiveAnim)
        } else {
            bindBgColor = blendColor(bgNormal, bgHover, bindHoverAnim)
        }

        val hideBgColor: Color?
        if (hideActiveAnim > 0.01f) {
            hideBgColor = blendColor(bgNormal, ClientSettings.INSTANCE.hideActiveBgColor, hideActiveAnim)
        } else {
            hideBgColor = blendColor(bgNormal, ClientSettings.INSTANCE.hideActiveColor, hideHoverAnim)
        }

        if (bindable) {
            drawRound(
                bindX, btnY, buttonWidth, buttonHeight, 3f,
                blendAlpha(bindBgColor, alpha * visAlpha)
            )
        }
        drawRound(
            hideX, btnY, buttonWidth, buttonHeight, 3f,
            blendAlpha(hideBgColor, alpha * visAlpha)
        )

        val bindText: String?
        if (this.isBinding) {
            bindText = "..."
        } else if (module.getKey() != 0) {
            bindText = getKeyName(module.getKey())
        } else {
            bindText = "Bind"
        }
        val hideText = if (module.isHidden) "Show" else "Hide"

        val textNormal = ClientSettings.INSTANCE.textNormalColor
        val textBind = ClientSettings.INSTANCE.accentColor
        val textHide = ClientSettings.INSTANCE.textHideColor

        val bindTextColor: Color
        if (this.isBinding) {
            bindTextColor = textBind
        } else if (bindActiveAnim > 0.01f) {
            bindTextColor = blendColor(textNormal, ClientSettings.INSTANCE.bindActiveTextColor, bindActiveAnim)
        } else {
            bindTextColor = blendColor(textNormal, textBind, bindHoverAnim)
        }

        val hideTextColor: Color?
        if (hideActiveAnim > 0.01f) {
            hideTextColor = blendColor(textNormal, ClientSettings.INSTANCE.hideActiveTextColor, hideActiveAnim)
        } else {
            hideTextColor = blendColor(textNormal, textHide, hideHoverAnim)
        }

        val hideTextW = Unfair.fontManager!!.getFont(13).getStringWidth(hideText).toFloat()

        if (bindable) {
            val bindTextW = Unfair.fontManager!!.getFont(13).getStringWidth(bindText).toFloat()
            Unfair.fontManager!!.getFont(13).drawString(
                bindText,
                bindX + (buttonWidth - bindTextW) / 2f, btnY + (buttonHeight - 13) / 2f + 2.5f,
                blendAlpha(bindTextColor, alpha * visAlpha).rgb, false
            )
        }
        Unfair.fontManager!!.getFont(13).drawString(
            hideText,
            hideX + (buttonWidth - hideTextW) / 2f, btnY + (buttonHeight - 13) / 2f + 2.5f,
            blendAlpha(hideTextColor, alpha * visAlpha).rgb, false
        )
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        val bindable = module.isBindable
        val bindX = x
        val hideX = if (bindable) x + buttonWidth + gap else x
        val btnY = y

        if (bindable && isHovering(mx, my, bindX, btnY, buttonWidth, buttonHeight)) {
            if (this.isBinding) {
                this.isBinding = false
                module.setKey(0)
            } else {
                this.isBinding = true
            }
            return
        }

        if (isHovering(mx, my, hideX, btnY, buttonWidth, buttonHeight)) {
            module.isHidden = !module.isHidden
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {}

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}

    override fun getHeight(): Float {
        return buttonHeight
    }

    override fun visible(): Boolean {
        return true
    }

    fun handleKeyInput(keyCode: Int): Boolean {
        if (this.isBinding && keyCode != 0) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                module.setKey(0)
                this.isBinding = false
                return true
            }
            module.setKey(keyCode)
            this.isBinding = false
            return true
        }
        return false
    }

    companion object {
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