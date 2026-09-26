package unfair.util

import net.minecraft.network.Packet

class TimedPacket {
    @JvmField
    val packet: Packet<*>?
    val time: Cold
    val millis: Long

    constructor(packet: Packet<*>?) {
        this.packet = packet
        this.time = Cold()
        this.millis = System.currentTimeMillis()
    }

    constructor(packet: Packet<*>?, millis: Long) {
        this.packet = packet
        this.millis = millis
        this.time = Cold()
    }

    val cold: Cold
        get() = this.time
}