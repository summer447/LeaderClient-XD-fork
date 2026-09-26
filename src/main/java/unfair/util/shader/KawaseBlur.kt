package unfair.util.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import unfair.util.RenderUtil
import unfair.util.shader.ShaderUtils.Companion.drawQuads
import kotlin.math.pow

object KawaseBlur {
    private val mc: Minecraft = Minecraft.getMinecraft()
    private val framebufferList: MutableList<Framebuffer> = ArrayList()
    var framebuffer: Framebuffer = Framebuffer(1, 1, false)
    private var kawaseDown: ShaderUtils? = null
    private var kawaseUp: ShaderUtils? = null
    private var currentIterations = 0

    private fun initShaders() {
        if (kawaseDown == null) {
            try {
                kawaseDown = ShaderUtils("kawaseDown")
            } catch (e: Exception) {
                System.err.println("Failed to initialize kawaseDown shader: " + e.message)
                e.printStackTrace()
            }
        }
        if (kawaseUp == null) {
            try {
                kawaseUp = ShaderUtils("kawaseUp")
            } catch (e: Exception) {
                System.err.println("Failed to initialize kawaseUp shader: " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun getKawaseDown(): ShaderUtils? {
        initShaders()
        return kawaseDown
    }

    private fun getKawaseUp(): ShaderUtils? {
        initShaders()
        return kawaseUp
    }

    private fun initFrameBuffers(iterations: Float) {
        for (framebuffer in framebufferList) {
            framebuffer.deleteFramebuffer()
        }
        framebufferList.clear()

        framebufferList.add(RenderUtil.createFrameBuffer(null).also { framebuffer = it })

        var i = 1
        while (i <= iterations) {
            val currentBuffer = Framebuffer(
                (mc.displayWidth / 3.0.pow(i.toDouble())).toInt(),
                (mc.displayHeight / 3.0.pow(i.toDouble())).toInt(),
                false
            )
            currentBuffer.setFramebufferFilter(GL11.GL_LINEAR)
            GlStateManager.bindTexture(currentBuffer.framebufferTexture)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT)
            GlStateManager.bindTexture(0)

            framebufferList.add(currentBuffer)
            i++
        }
    }

    @JvmStatic
    fun renderBlur(stencilFrameBufferTexture: Int, iterations: Int, offset: Float) {
        // Initialize shaders on first use
        initShaders()

        // Check if shaders are properly initialized
        if (kawaseDown == null || kawaseUp == null) {
            System.err.println("KawaseBlur shaders not initialized, skipping blur render")
            return
        }

        if (currentIterations != iterations || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            initFrameBuffers(iterations.toFloat())
            currentIterations = iterations
        }

        renderFBO(framebufferList[1], mc.framebuffer.framebufferTexture, kawaseDown, offset)

        for (i in 1..<iterations) {
            renderFBO(framebufferList[i + 1], framebufferList[i].framebufferTexture, kawaseDown, offset)
        }

        for (i in iterations downTo 2) {
            renderFBO(framebufferList[i - 1], framebufferList[i].framebufferTexture, kawaseUp, offset)
        }


        val lastBuffer = framebufferList[0]
        lastBuffer.framebufferClear()
        lastBuffer.bindFramebuffer(false)

        kawaseUp!!.init()
        kawaseUp!!.setUniformf("offset", offset, offset)
        kawaseUp!!.setUniformi("inTexture", 0)
        kawaseUp!!.setUniformi("check", 1)
        kawaseUp!!.setUniformi("textureToCheck", 16)
        kawaseUp!!.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight)
        kawaseUp!!.setUniformf(
            "iResolution",
            lastBuffer.framebufferWidth.toFloat(),
            lastBuffer.framebufferHeight.toFloat()
        )
        GL13.glActiveTexture(GL13.GL_TEXTURE16)
        RenderUtil.bindTexture(stencilFrameBufferTexture)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        RenderUtil.bindTexture(framebufferList[1].framebufferTexture)
        drawQuads()
        kawaseUp!!.unload()

        mc.framebuffer.bindFramebuffer(true)
        RenderUtil.bindTexture(framebufferList[0].framebufferTexture)
        RenderUtil.setAlphaLimit(0f)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        drawQuads()
        GlStateManager.bindTexture(0)
        GlStateManager.disableBlend()
    }

    private fun renderFBO(framebuffer: Framebuffer, framebufferTexture: Int, shader: ShaderUtils?, offset: Float) {
        if (shader == null) return
        framebuffer.framebufferClear()
        framebuffer.bindFramebuffer(false)
        shader.init()
        RenderUtil.bindTexture(framebufferTexture)
        shader.setUniformf("offset", offset, offset)
        shader.setUniformi("inTexture", 0)
        shader.setUniformi("check", 0)
        shader.setUniformf("halfpixel", 1.0f / framebuffer.framebufferWidth, 1.0f / framebuffer.framebufferHeight)
        shader.setUniformf(
            "iResolution",
            framebuffer.framebufferWidth.toFloat(),
            framebuffer.framebufferHeight.toFloat()
        )
        drawQuads()
        shader.unload()
    }
}
