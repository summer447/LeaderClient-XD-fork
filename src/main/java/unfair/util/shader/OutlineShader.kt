@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package unfair.util.shader

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL20
import java.lang.String

class OutlineShader : Shader(shader) {
    override fun onLink() {
        this.setUniform("texture")
        this.setUniform("size")
        this.setUniform("radius")
    }

    override fun onUse() {
        GL20.glUseProgram(this.programId)
        val texLoc = this.getUniformLocationCached("texture")
        GL20.glUniform1i(texLoc, 0)
        val sizeLoc = this.getUniformLocationCached("size")
        val invW = 1.0f / Minecraft.getMinecraft().displayWidth
        val invH = 1.0f / Minecraft.getMinecraft().displayHeight
        GL20.glUniform2f(sizeLoc, invW, invH)
        val radiusLoc = this.getUniformLocationCached("radius")
        GL20.glUniform1f(radiusLoc, 2.0f)
    }

    companion object {
        private val shader: kotlin.String = String.join(
            "\n",
            "uniform sampler2D texture;",
            "uniform vec2 size;",
            "uniform float radius;",
            "void main(void) {",
            "vec4 xy = texture2D(texture, gl_TexCoord[0].xy);",
            "if(xy.a != 0) {",
            "gl_FragColor = vec4(0, 0, 0, 0);",
            "} else {",
            "for (float x = -radius; x <= radius; x++) {",
            "for (float y = -radius; y <= radius; y++) {",
            "vec4 color = texture2D(texture, gl_TexCoord[0].xy + vec2(size.x * x, size.y * y));",
            "if (color.a != 0) {",
            "gl_FragColor = color;",
            "}",
            "}",
            "}",
            "}",
            "}"
        )
    }
}
