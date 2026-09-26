package unfair.ui.clickgui.oldgui.callback

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import java.io.IOException
import java.util.function.Consumer

class GuiInput(
    private val title: String?,
    private val defaultValue: String?,
    private val callback: Consumer<String?>?,
    private val caller: GuiScreen?
) : GuiScreen() {
    private var textField: GuiTextField? = null
    private var buttonOk: GuiButton? = null

    override fun initGui() {
        val centerX = this.width / 2
        val centerY = this.height / 2

        textField = GuiTextField(0, this.fontRendererObj, centerX - 100, centerY - 10, 200, 20)
        textField!!.text = defaultValue
        textField!!.isFocused = true

        this.buttonList.add(GuiButton(0, centerX - 100, centerY + 20, 95, 20, "Confirm").also { buttonOk = it })
        this.buttonList.add(GuiButton(1, centerX + 5, centerY + 20, 95, 20, "Cancel"))
    }

    override fun actionPerformed(button: GuiButton?) {
        if (button === buttonOk) {
            callback?.accept(textField!!.text)
        }
        this.mc.displayGuiScreen(caller)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        textField!!.textboxKeyTyped(typedChar, keyCode)
    }

    protected override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        textField!!.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 35, 0xFFFFFF)
        textField!!.drawTextBox()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun updateScreen() {
        textField!!.updateCursorCounter()
    }

    companion object {
        @JvmStatic
        fun prompt(title: String?, defaultValue: String?, callback: Consumer<String?>?, caller: GuiScreen?) {
            Minecraft.getMinecraft().displayGuiScreen(GuiInput(title, defaultValue, callback, caller))
        }
    }
}
