package unfair.util.shader

import org.lwjgl.opengl.GL20
import java.awt.Color
import java.lang.String

class GlowShader : Shader(shader) {
    public override fun onLink() {
        this.setUniform("texture")
        this.setUniform("color")
    }

    public override fun onUse() {
        GL20.glUseProgram(this.programId)
        val texLoc = this.getUniformLocationCached("texture")
        GL20.glUniform1i(texLoc, 0)
        GL20.glUniform4f(texLoc, 1.0f, 1.0f, 1.0f, 1.0f)
    }

    fun W(color: Color) {
        GL20.glUniform4f(
            this.getUniformLocationCached("color"),
            color.getRed().toFloat() / 255.0f,
            color.getGreen().toFloat() / 255.0f,
            color.getBlue().toFloat() / 255.0f,
            color.getAlpha().toFloat() / 255.0f
        )
    }

    companion object {
        private val shader: kotlin.String = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D texture;",
            "uniform vec4 color;",
            "void main() {",
            "vec4 st = texture2D(texture, gl_TexCoord[0].st);",
            "gl_FragColor = vec4(color.rgb, st.a > 0.0 ? color.a : 0.0);",
            "}"
        )
    }
}
