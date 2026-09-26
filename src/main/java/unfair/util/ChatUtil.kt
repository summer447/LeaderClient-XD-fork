package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent
import unfair.enums.ChatColors

object ChatUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    fun send(iChatComponent: IChatComponent?) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(iChatComponent)
        }
    }

    @JvmStatic
    fun sendFormatted(string: String) {
        send(ChatComponentText(ChatColors.formatColor(string)))
    }

    @JvmStatic
    fun sendRaw(string: String?) {
        send(ChatComponentText(string))
    }

    @JvmStatic
    fun sendMessage(string: String?) {
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage(string)
        }
    }
}
