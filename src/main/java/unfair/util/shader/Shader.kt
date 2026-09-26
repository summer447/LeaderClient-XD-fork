package unfair.util.shader

import org.lwjgl.opengl.GL20

abstract class Shader(string: String) {
    private val uniformLocations: MutableMap<String?, Int?> = HashMap()

    @JvmField
    protected var programId: Int = 0

    init {
        this.createProgram(string)
    }

    private fun compileShader(source: String, type: Int): Int {
        val shader = GL20.glCreateShader(type)
        GL20.glShaderSource(shader, source)
        GL20.glCompileShader(shader)
        val compile = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS)
        return if (compile == 0) -1 else shader
    }

    private fun createProgram(fragment: String) {
        this.programId = GL20.glCreateProgram()
        GL20.glAttachShader(this.programId, this.compileShader(vertex, GL20.GL_VERTEX_SHADER))
        GL20.glAttachShader(this.programId, this.compileShader(fragment, GL20.GL_FRAGMENT_SHADER))
        GL20.glLinkProgram(this.programId)
        val programId = GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS)
        if (programId == 0) {
            this.programId = -1
        } else {
            this.onLink()
        }
    }

    fun getUniformLocationCached(name: String?): Int {
        return this.uniformLocations.get(name)!!
    }

    fun setUniform(name: String) {
        this.uniformLocations[name] = GL20.glGetUniformLocation(this.programId, name)
    }

    abstract fun onLink()

    abstract fun onUse()

    fun use() {
        onUse()
    }

    fun stop() {
        GL20.glUseProgram(0)
    }

    companion object {
        private val vertex = "#version 120\n" +
                "void main(void) {\n" +
                "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}"
    }
}
