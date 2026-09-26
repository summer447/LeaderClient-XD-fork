package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.network.INetHandler
import net.minecraft.network.Packet
import net.minecraft.network.ThreadQuickExitException
import net.minecraft.network.play.INetHandlerPlayClient

object PacketUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()
    var skipReceiveEvent: MutableList<Packet<INetHandlerPlayClient?>?> = ArrayList()

    @JvmStatic
    fun sendPacket(packet: Packet<*>?) {
        mc.netHandler.networkManager.sendPacket(packet)
    }

    @JvmStatic
    fun sendPacketNoEvent(packet: Packet<*>?) {
        mc.netHandler.networkManager.sendPacket(packet, null)
    }

    fun receivePacketNoEvent(packet: Packet<*>?) {
        if (packet == null) return
        try {
            val casted = castPacket<INetHandlerPlayClient?>(packet)
            skipReceiveEvent.add(casted)
            casted.processPacket(mc.netHandler)
        } catch (_: ThreadQuickExitException) {
        }
    }


    @JvmStatic
    fun receivePacket(packet: Packet<*>?) {
        if (packet == null) return
        try {
            val casted = castPacket<INetHandlerPlayClient?>(packet)
            casted.processPacket(mc.netHandler)
        } catch (_: ThreadQuickExitException) {
        }
    }

    @Throws(ClassCastException::class)
    fun <H : INetHandler?> castPacket(packet: Packet<*>): Packet<H?> {
        return packet as Packet<H?>
    }
}
