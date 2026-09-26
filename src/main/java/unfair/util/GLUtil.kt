package unfair.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.GLU

object GLUtil {
    private val mc: Minecraft = Minecraft.getMinecraft()

    @JvmStatic
    fun project2D(x: Float, y: Float, z: Float, scaleFactor: Int): FloatArray? {
        val viewport = BufferUtils.createIntBuffer(16)
        val modelView = BufferUtils.createFloatBuffer(16)
        val projection = BufferUtils.createFloatBuffer(16)
        val result = BufferUtils.createFloatBuffer(4)

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection)
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport)

        if (GLU.gluProject(x, y, z, modelView, projection, viewport, result)) {
            val sr = ScaledResolution(mc)
            return floatArrayOf(
                result.get(0) / scaleFactor,
                (sr.scaledHeight - result.get(1) / scaleFactor),
                result.get(2)
            )
        }
        return null
    }
}