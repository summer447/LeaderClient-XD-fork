package unfair.ui.clickgui.panel.config

import unfair.Unfair
import unfair.config.Config
import unfair.management.ClientSettings
import unfair.ui.clickgui.panel.PanelValueItem
import unfair.util.ChatUtil.sendFormatted
import unfair.util.shader.RoundedUtils.drawRound
import java.awt.Color
import java.io.File

class ConfigEntry(configName: String) : PanelValueItem() {
    @JvmField
    val configName: String = if (configName.endsWith(".json")) configName.substring(0, configName.length - 5) else configName
    private var isActive: Boolean
    private var isHovering = false

    init {
        this.isActive = this.configName == Config.lastConfig
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
    }

    override fun render(mouseX: Int, mouseY: Int) {
        isHovering = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height

        val cardH = height

        val cardNormal = ClientSettings.INSTANCE.cardColor
        val cardActive = ClientSettings.INSTANCE.entryActiveColor
        val cardHover = ClientSettings.INSTANCE.entryHoverColor

        val bgColor: Color
        if (isActive) {
            bgColor = cardActive
        } else if (isHovering) {
            bgColor = cardHover
        } else {
            bgColor = cardNormal
        }

        drawRound(x, y, width, cardH, 5f, blendAlpha(bgColor, alpha))

        val iconX = x + 12
        val iconY = y + (cardH - 16) / 2f
        Unfair.fontManager!!.icon16!!.drawString(
            "\uf0c7", iconX, iconY + 4.5f,
            blendAlpha(ClientSettings.INSTANCE.entryIconColor, alpha).rgb, false
        )

        val textX = iconX + 22
        val textY = y + (cardH - 18) / 2f

        val textColor: Int =
            if (isActive) blendAlpha(ClientSettings.INSTANCE.entryValueColor, alpha).rgb else blendAlpha(
                ClientSettings.INSTANCE.configNameInactiveColor,
                alpha
            ).rgb

        Unfair.fontManager!!.getFont(18).drawString(configName, textX, textY + 1.5f, textColor, false)

        if (isActive) {
            val activeTag = "Active"
            val tagW = Unfair.fontManager!!.getFont(14).getStringWidth(activeTag).toFloat()
            val tagFinalX = textX + Unfair.fontManager!!.getFont(18).getStringWidth(configName) + 8

            drawRound(
                tagFinalX, textY, tagW + 12, 16f, 3f,
                blendAlpha(ClientSettings.INSTANCE.buttonPrimaryColor, alpha * 0.9f)
            )
            Unfair.fontManager!!.getFont(14).drawString(
                activeTag, tagFinalX + 6, textY + 4,
                blendAlpha(ClientSettings.INSTANCE.cardColor, alpha).rgb, false
            )
        }

        val btnY = y + (cardH - 18) / 2f + 1
        val btnStartX = x + width - 200

        drawSmallButton(
            btnStartX, btnY, "Load", ClientSettings.INSTANCE.buttonPrimaryColor,
            isBtnHovered(mouseX, mouseY, btnStartX, btnY, 48f, 18f)
        )
        drawSmallButton(
            btnStartX + 52, btnY, "Save", ClientSettings.INSTANCE.buttonPrimaryColor,
            isBtnHovered(mouseX, mouseY, btnStartX + 52, btnY, 48f, 18f)
        )
        drawSmallButton(
            btnStartX + 104, btnY, "Delete", ClientSettings.INSTANCE.buttonDangerColor,
            isBtnHovered(mouseX, mouseY, btnStartX + 104, btnY, 56f, 18f)
        )
    }

    private fun drawSmallButton(bx: Float, by: Float, text: String, baseColor: Color, hovered: Boolean) {
        val btnW = (if (text == "Delete") 56 else 48).toFloat()
        val btnH = 18f

        val color = if (hovered) baseColor.brighter() else baseColor

        drawRound(bx, by, btnW, btnH, 4f, blendAlpha(color, alpha))

        val textW = Unfair.fontManager!!.getFont(14).getStringWidth(text).toFloat()
        Unfair.fontManager!!.getFont(14).drawString(
            text,
            bx + (btnW - textW) / 2f, by + (btnH - 14) / 2f + 2.0f,
            blendAlpha(ClientSettings.INSTANCE.cardColor, alpha).rgb, false
        )
    }

    private fun isBtnHovered(mx: Int, my: Int, bx: Float, by: Float, bw: Float, bh: Float): Boolean {
        return isHovering && mx >= bx && mx <= bx + bw && my >= by && my <= by + bh
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) {
        if (button != 0) return

        val hovering = mx >= x && mx <= x + width && my >= y && my <= y + height
        if (!hovering) return

        val cardH = height
        val btnY = y + (cardH - 18) / 2f + 1
        val btnStartX = x + width - 200

        if (isBtnHovered(mx, my, btnStartX, btnY, 48f, 18f)) {
            loadConfig()
        } else if (isBtnHovered(mx, my, btnStartX + 52, btnY, 48f, 18f)) {
            saveConfig()
        } else if (isBtnHovered(mx, my, btnStartX + 104, btnY, 56f, 18f)) {
            deleteConfig()
        }
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) {}

    override fun mouseDragged(mx: Int, my: Int, button: Int) {}

    override fun getHeight(): Float {
        return 34f
    }

    override fun visible(): Boolean {
        return true
    }

    fun isHovering(mx: Int, my: Int): Boolean {
        return mx >= x && mx <= x + width && my >= y && my <= y + height
    }

    private fun loadConfig() {
        try {
            val config = Config(configName, false)
            config.load()
            setActive(true)
            sendFormatted(Unfair.clientName + "&aLoaded: &f" + configName)
        } catch (_: Exception) {
            sendFormatted(Unfair.clientName + "&cFailed to load: &f" + configName)
        }
    }

    private fun saveConfig() {
        try {
            val config = Config(configName, false)
            config.save()
            setActive(true)
            sendFormatted(Unfair.clientName + "&aSaved: &f" + configName)
        } catch (_: Exception) {
            sendFormatted(Unfair.clientName + "&cFailed to save: &f" + configName)
        }
    }

    private fun deleteConfig() {
        try {
            val file = File("./config/Unfair/$configName.json")
            if (file.exists()) {
                file.delete()
                sendFormatted(Unfair.clientName + "&cDeleted: &f" + configName)
                if (isActive) {
                    val defaultCfg = Config("default", false)
                    if (defaultCfg.file.exists()) defaultCfg.load()
                    setActive(false)
                }
            }
        } catch (_: Exception) {
            sendFormatted(Unfair.clientName + "&cFailed to delete: &f" + configName)
        }
    }

    fun setActive(active: Boolean) {
        this.isActive = active
        if (active) Config.lastConfig = configName
    }

    fun resetAnimations() {}

    companion object {
        private fun blendAlpha(c: Color, a: Float): Color {
            return Color(c.red, c.green, c.blue, (a * 255).toInt())
        }
    }
}
