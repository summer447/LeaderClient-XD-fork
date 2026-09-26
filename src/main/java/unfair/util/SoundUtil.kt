package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.util.ResourceLocation

object SoundUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    fun playSound(soundName: String) {
        val soundHandler = mc.getSoundHandler()
        if (soundHandler != null) {
            val positionedSoundRecord = PositionedSoundRecord.create(ResourceLocation(soundName))
            soundHandler.playSound(positionedSoundRecord)
        }
    }
}
