package unfair.util.shader

import net.minecraft.client.shader.Framebuffer
import unfair.util.RenderUtil

object BlurUtils {
    private var stencilFrameBufferBlur = Framebuffer(1, 1, false)
    private var stencilFrameBufferBloom = Framebuffer(1, 1, false)

    @JvmStatic
    fun prepareBlur() {
        stencilFrameBufferBlur = RenderUtil.createFrameBuffer(stencilFrameBufferBlur)
        stencilFrameBufferBlur.framebufferClear()
        stencilFrameBufferBlur.bindFramebuffer(false)
    }

    @JvmStatic
    fun prepareBloom() {
        stencilFrameBufferBloom = RenderUtil.createFrameBuffer(stencilFrameBufferBloom)
        stencilFrameBufferBloom.framebufferClear()
        stencilFrameBufferBloom.bindFramebuffer(false)
    }

    @JvmStatic
    fun blurEnd(passes: Int, radius: Float) {
        stencilFrameBufferBlur.unbindFramebuffer()
        KawaseBlur.renderBlur(stencilFrameBufferBlur.framebufferTexture, passes, radius)
    }

    @JvmStatic
    fun bloomEnd(passes: Int, radius: Float) {
        stencilFrameBufferBloom.unbindFramebuffer()
        KawaseBloom.renderBlur(stencilFrameBufferBloom.framebufferTexture, passes, radius)
    }
}
