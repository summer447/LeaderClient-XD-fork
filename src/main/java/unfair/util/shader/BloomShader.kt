package unfair.util.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.shader.Framebuffer
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import unfair.util.RenderUtil
import unfair.util.shader.ShaderUtils.Companion.drawQuads
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.pow

object BloomShader {
    private val mc: Minecraft = Minecraft.getMinecraft()
    private val KAWASE_DOWN = ShaderUtils("kawaseDownBloom")
    private val KAWASE_UP = ShaderUtils("kawaseUpBloom")
    private var framebuffer: Framebuffer? = Framebuffer(1, 1, true)
    private var inputFramebuffer: Framebuffer? = null
    private var currentIterations = 0
    private val framebufferList: MutableList<Framebuffer> = ArrayList<Framebuffer>()

    private fun ensureInputFramebuffer(): Framebuffer {
        if (inputFramebuffer == null || inputFramebuffer!!.framebufferWidth != mc.displayWidth || inputFramebuffer!!.framebufferHeight != mc.displayHeight) {
            inputFramebuffer = Framebuffer(mc.displayWidth, mc.displayHeight, true)
        }
        return inputFramebuffer!!
    }

    fun beginFramebuffer(): Framebuffer {
        val fb = ensureInputFramebuffer()
        GL11.glClearColor(0f, 0f, 0f, 0f)
        fb.framebufferClear()
        fb.bindFramebuffer(false)
        return fb
    }

    private fun initFramebuffers(iterations: Float) {
        framebufferList.forEach(Consumer { obj: Framebuffer? -> obj!!.deleteFramebuffer() })
        framebufferList.clear()
        framebuffer = null
        framebufferList.add(RenderUtil.createFrameBuffer(null, true).also { framebuffer = it })
        var i = 1
        while (i <= iterations) {
            val currentBuffer = Framebuffer(
                (mc.displayWidth / 2.0.pow(i.toDouble())).toInt(),
                (mc.displayHeight / 2.0.pow(i.toDouble())).toInt(),
                true
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

    fun renderBloom(framebufferTexture: Int, iterations: Int, offset: Int) {
        var iterations = iterations
        var offset = offset
        iterations = max(1, iterations)
        offset = max(1, offset)
        if (currentIterations != iterations || framebuffer!!.framebufferWidth != mc.displayWidth || framebuffer!!.framebufferHeight != mc.displayHeight) {
            initFramebuffers(iterations.toFloat())
            currentIterations = iterations
        }
        RenderUtil.setAlphaLimit(0f)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE)
        GL11.glClearColor(0f, 0f, 0f, 0f)
        renderDownFBO(framebufferList[1], framebufferTexture, offset.toFloat())
        for (i in 1..<iterations) {
            renderDownFBO(framebufferList[i + 1], framebufferList[i].framebufferTexture, offset.toFloat())
        }
        for (i in iterations downTo 2) {
            renderUpFBO(framebufferList[i - 1], framebufferList[i].framebufferTexture, offset.toFloat())
        }
        val lastBuffer = framebufferList[0]
        lastBuffer.framebufferClear()
        lastBuffer.bindFramebuffer(false)
        KAWASE_UP.init()
        KAWASE_UP.setUniformf("offset", offset.toFloat(), offset.toFloat())
        KAWASE_UP.setUniformf("halfpixel", 1.0f / lastBuffer.framebufferWidth, 1.0f / lastBuffer.framebufferHeight)
        KAWASE_UP.setUniformf(
            "iResolution",
            lastBuffer.framebufferWidth.toFloat(),
            lastBuffer.framebufferHeight.toFloat()
        )
        KAWASE_UP.setUniformi("inTexture", 0)
        KAWASE_UP.setUniformi("check", 1)
        KAWASE_UP.setUniformi("textureToCheck", 1)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1)
        RenderUtil.bindTexture(framebufferTexture)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
        RenderUtil.bindTexture(framebufferList[1].framebufferTexture)
        drawQuads()
        KAWASE_UP.unload()
        GlStateManager.clearColor(0f, 0f, 0f, 0f)
        mc.framebuffer.bindFramebuffer(false)
        RenderUtil.setAlphaLimit(0f)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        RenderUtil.bindTexture(framebufferList[0].framebufferTexture)
        RenderUtil.drawQuads()
        GlStateManager.bindTexture(0)
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0)
    }

    private fun renderDownFBO(fb: Framebuffer, texture: Int, offset: Float) {
        fb.framebufferClear()
        fb.bindFramebuffer(false)
        KAWASE_DOWN.init()
        KAWASE_DOWN.setUniformf("offset", offset, offset)
        KAWASE_DOWN.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight)
        KAWASE_DOWN.setUniformf("iResolution", fb.framebufferWidth.toFloat(), fb.framebufferHeight.toFloat())
        KAWASE_DOWN.setUniformi("inTexture", 0)
        RenderUtil.bindTexture(texture)
        drawQuads()
        KAWASE_DOWN.unload()
    }

    private fun renderUpFBO(fb: Framebuffer, texture: Int, offset: Float) {
        fb.framebufferClear()
        fb.bindFramebuffer(false)
        KAWASE_UP.init()
        KAWASE_UP.setUniformf("offset", offset, offset)
        KAWASE_UP.setUniformf("halfpixel", 1.0f / fb.framebufferWidth, 1.0f / fb.framebufferHeight)
        KAWASE_UP.setUniformf("iResolution", fb.framebufferWidth.toFloat(), fb.framebufferHeight.toFloat())
        KAWASE_UP.setUniformi("inTexture", 0)
        KAWASE_UP.setUniformi("check", 0)
        KAWASE_UP.setUniformi("textureToCheck", 1)
        RenderUtil.bindTexture(texture)
        drawQuads()
        KAWASE_UP.unload()
    }
}