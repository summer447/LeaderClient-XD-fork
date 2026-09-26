package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution

class NotificationTask(@JvmField var message: String?, var duration: Long) {
    var start: Long = System.currentTimeMillis()

    @JvmField
    var animationX: Float = ScaledResolution(Minecraft.getMinecraft()).scaledWidth.toFloat()

    @JvmField
    var targetX: Float = 0f

    val isFinished: Boolean
        get() = System.currentTimeMillis() - start > duration

    val progress: Float
        get() = (System.currentTimeMillis() - start).toFloat() / duration.toFloat()
}
