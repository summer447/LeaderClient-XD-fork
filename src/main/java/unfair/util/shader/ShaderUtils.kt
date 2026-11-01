package unfair.util.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class ShaderUtils @JvmOverloads constructor(
    fragmentShaderLoc: String,
    vertexShaderLoc: String = "minecraft:shaders/vertex.vsh"
) {
    @JvmField
    val programID: Int
    private val kawaseUpGlow = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform bool check;\n" +
            "uniform float lastPass;\n" +
            "uniform float exposure;\n" +
            "\n" +
            "void main() {\n" +
            "    if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smpl1 =  texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n" +
            "    smpl1.rgb *= smpl1.a;\n" +
            "    sum += smpl1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, 1.0 - exp(-result.a * exposure), step(0.0, lastPass)));\n" +
            "}"
    private val glowShader = "#version 120\n" +
            "\n" +
            "uniform sampler2D textureIn, textureToCheck;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform vec3 color;\n" +
            "uniform bool avoidTexture;\n" +
            "uniform float exposure, radius;\n" +
            "uniform float weights[256];\n" +
            "\n" +
            "#define offset direction * texelSize\n" +
            "\n" +
            "void main() {\n" +
            "    if (direction.y == 1 && avoidTexture) {\n" +
            "        if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n" +
            "    }\n" +
            "    vec4 innerColor = texture2D(textureIn, gl_TexCoord[0].st);\n" +
            "    innerColor.rgb *= innerColor.a;\n" +
            "    innerColor *= weights[0];\n" +
            "    for (float r = 1.0; r <= radius; r++) {\n" +
            "        vec4 colorCurrent1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);\n" +
            "        vec4 colorCurrent2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);\n" +
            "\n" +
            "        colorCurrent1.rgb *= colorCurrent1.a;\n" +
            "        colorCurrent2.rgb *= colorCurrent2.a;\n" +
            "\n" +
            "        innerColor += (colorCurrent1 + colorCurrent2) * weights[int(r)];\n" +
            "    }\n" +
            "\n" +
            "    gl_FragColor = vec4(innerColor.rgb / innerColor.a, mix(innerColor.a, 1.0 - exp(-innerColor.a * exposure), step(0.0, direction.y)));\n" +
            "}\n"
    private val roundRectTexture = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D textureIn;\n" +
            "uniform float radius, alpha;\n" +
            "\n" +
            "float roundedBoxSDF(vec2 centerPos, vec2 size, float radius) {\n" +
            "    return length(max(abs(centerPos) -size, 0.)) - radius;\n" +
            "}\n" +
            "\n" +
            "\n" +
            "void main() {\n" +
            "    float distance = roundedBoxSDF((rectSize * .5) - (gl_TexCoord[0].st * rectSize), (rectSize * .5) - radius - 1., radius);\n" +
            "    float smoothedAlpha =  (1.0-smoothstep(0.0, 2.0, distance)) * alpha;\n" +
            "    gl_FragColor = vec4(texture2D(textureIn, gl_TexCoord[0].st).rgb, smoothedAlpha);\n" +
            "}"
    private val roundRectOutline = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color, outlineColor;\n" +
            "uniform float radius, outlineThickness;\n" +
            "\n" +
            "float roundedSDF(vec2 centerPos, vec2 size, float radius) {\n" +
            "    return length(max(abs(centerPos) - size + radius, 0.0)) - radius;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    float distance = roundedSDF(gl_FragCoord.xy - location - (rectSize * .5), (rectSize * .5) + (outlineThickness *.5) - 1.0, radius);\n" +
            "\n" +
            "    float blendAmount = smoothstep(0., 2., abs(distance) - (outlineThickness * .5));\n" +
            "\n" +
            "    vec4 insideColor = (distance < 0.) ? color : vec4(outlineColor.rgb,  0.0);\n" +
            "    gl_FragColor = mix(outlineColor, insideColor, blendAmount);\n" +
            "\n" +
            "}"
    private val kawaseUpBloom = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform int check;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = gl_FragCoord.xy / iResolution;\n" +
            "\n" +
            "    vec2 offset1 = vec2(-halfpixel.x, 0.0) * offset;\n" +
            "    vec2 offset2 = vec2(-halfpixel.x, halfpixel.y) * offset;\n" +
            "    vec2 offset3 = vec2(0.0, halfpixel.y * 2.0) * offset;\n" +
            "    vec2 offset4 = vec2(halfpixel.x, halfpixel.y) * offset;\n" +
            "    vec2 offset5 = vec2(halfpixel.x * 2.0, 0.0) * offset;\n" +
            "    vec2 offset6 = vec2(halfpixel.x, -halfpixel.y) * offset;\n" +
            "    vec2 offset7 = vec2(0.0, -halfpixel.y * 2.0) * offset;\n" +
            "    vec2 offset8 = vec2(-halfpixel.x, -halfpixel.y) * offset;\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv + offset1);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smpl1 = texture2D(inTexture, uv + offset2);\n" +
            "    smpl1.rgb *= smpl1.a;\n" +
            "    sum += smpl1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + offset3);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + offset4);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + offset5);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + offset6);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + offset7);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + offset8);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    float checkAlpha = texture2D(textureToCheck, gl_TexCoord[0].st).a;\n" +
            "    gl_FragColor = vec4(result.rgb / result.a, mix(result.a, result.a * (1.0 - checkAlpha), float(check)));\n" +
            "}\n"
    private val kawaseDownBloom = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = gl_FragCoord.xy / iResolution;\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    sum *= 4.0;\n" +
            "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    smp1.rgb *= smp1.a;\n" +
            "    sum += smp1;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 result = sum / 8.0;\n" +
            "    gl_FragColor = vec4(result.rgb / result.a, result.a);\n" +
            "}\n"
    private val kawaseUp = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform int check;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = gl_FragCoord.xy / iResolution;\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "\n" +
            "    vec4 average = sum / 12.0;\n" +
            "    gl_FragColor = vec4(average.rgb, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n" +
            "}"
    private val kawaseDown = "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = gl_FragCoord.xy / iResolution;\n" +
            "\n" +
            "    vec4 sum = texture2D(inTexture, uv) * 4.0;\n" +
            "    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "\n" +
            "    gl_FragColor = vec4(sum.rgb * 0.125, 1.0);\n" +
            "}"
    private val gradientMask = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec3 color1, color2, color3, color4;\n" +
            "uniform float alpha;\n" +
            "\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "vec3 createGradient(vec2 coords, vec3 color1, vec3 color2, vec3 color3, vec3 color4){\n" +
            "    vec3 color = mix(mix(color1.rgb, color2.rgb, coords.y), mix(color3.rgb, color4.rgb, coords.y), coords.x);\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898,78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n" +
            "    float texColorAlpha = texture2D(tex, gl_TexCoord[0].st).a;\n" +
            "    gl_FragColor = vec4(createGradient(coords, color1, color2, color3, color4), texColorAlpha * alpha);\n" +
            "}"
    private val mask = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D u_texture, u_texture2;\n" +
            "void main() {\n" +
            "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n" +
            "    float texColorAlpha = texture2D(u_texture, gl_TexCoord[0].st).a;\n" +
            "    vec3 tex2Color = texture2D(u_texture2, gl_TexCoord[0].st).rgb;\n" +
            "    gl_FragColor = vec4(tex2Color, texColorAlpha);\n" +
            "}"
    private val gradient = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform sampler2D tex;\n" +
            "uniform vec4 color1, color2, color3, color4;\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n" +
            "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 coords = (gl_FragCoord.xy - location) / rectSize;\n" +
            "    gl_FragColor = createGradient(coords, color1, color2, color3, color4);\n" +
            "}"
    private val roundedRectGradient = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color1, color2, color3, color4;\n" +
            "uniform float radius;\n" +
            "\n" +
            "#define NOISE .5/255.0\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    return length(max(abs(p) - b , 0.0)) - r;\n" +
            "}\n" +
            "\n" +
            "vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){\n" +
            "    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);\n" +
            "    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));\n" +
            "    return color;\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 st = gl_TexCoord[0].st;\n" +
            "    vec2 halfSize = rectSize * .5;\n" +
            "    \n" +
            "   float smoothedAlpha =  (1.0-smoothstep(0.0, 2., roundSDF(halfSize - (gl_TexCoord[0].st * rectSize), halfSize - radius - 1., radius)));\n" +
            "    vec4 gradient = createGradient(st, color1, color2, color3, color4);\n" +
            "    gl_FragColor = vec4(gradient.rgb, gradient.a * smoothedAlpha);\n" +
            "}"
    private val roundedRect = "#version 120\n" +
            "\n" +
            "uniform vec2 location, rectSize;\n" +
            "uniform vec4 color;\n" +
            "uniform float radius;\n" +
            "uniform bool blur;\n" +
            "\n" +
            "float roundSDF(vec2 p, vec2 b, float r) {\n" +
            "    return length(max(abs(p) - b, 0.0)) - r;\n" +
            "}\n" +
            "\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 rectHalf = rectSize * .5;\n" +
            "    float smoothedAlpha =  (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1., radius))) * color.a;\n" +
            "    gl_FragColor = vec4(color.rgb, smoothedAlpha);\n" +
            "\n" +
            "}"
    private val roundedRectRise = "#version 120\n" +
            "\n" +
            "uniform vec2 u_size;\n" +
            "uniform float u_radius;\n" +
            "uniform vec4 u_color;\n" +
            "uniform vec4 u_edges;\n" +
            "\n" +
            "void main(void)\n" +
            "{\n" +
            "    vec2 tex_coord = gl_TexCoord[0].st;\n" +
            "\n" +
            "    if (tex_coord.x < 0.5 && tex_coord.y > 0.5 && u_edges.x == 0.0 ||\n" +
            "        tex_coord.x > 0.5 && tex_coord.y > 0.5 && u_edges.y == 0.0 ||\n" +
            "        tex_coord.x > 0.5 && tex_coord.y < 0.5 && u_edges.z == 0.0 ||\n" +
            "        tex_coord.x < 0.5 && tex_coord.y < 0.5 && u_edges.w == 0.0) {\n" +
            "        gl_FragColor = u_color;\n" +
            "    } else {\n" +
            "        gl_FragColor = vec4(u_color.rgb, u_color.a * smoothstep(1.0, 0.0, length(max((abs(tex_coord - 0.5) + 0.5) * u_size - u_size + u_radius, 0.0)) - u_radius + 0.5));\n" +
            "    }\n" +
            "}"
    private val vertexShader = "#version 120\n" +
            "\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}"
    private val mc: Minecraft = Minecraft.getMinecraft()

    init {
        val program = GL20.glCreateProgram()
        check(program != 0) { "Failed to create GL program!" }

        try {
            val fragmentShaderID: Int
            when (fragmentShaderLoc) {
                "kawaseUpGlow" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(kawaseUpGlow.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "glow" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(glowShader.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "roundRectTexture" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(roundRectTexture.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "roundRectOutline" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(roundRectOutline.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "kawaseUpBloom" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(kawaseUpBloom.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "kawaseDownBloom" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(kawaseDownBloom.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "kawaseUp" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(kawaseUp.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "kawaseDown" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(kawaseDown.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "gradientMask" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(gradientMask.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "mask" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(mask.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "gradient" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(gradient.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "roundedRect" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(roundedRect.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "roundedRectGradient" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(roundedRectGradient.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                "roundedRectRise" -> fragmentShaderID =
                    createShader(ByteArrayInputStream(roundedRectRise.toByteArray()), GL20.GL_FRAGMENT_SHADER)

                else -> fragmentShaderID = createShader(
                    mc.resourceManager.getResource(ResourceLocation(fragmentShaderLoc)).inputStream,
                    GL20.GL_FRAGMENT_SHADER
                )
            }
            GL20.glAttachShader(program, fragmentShaderID)

            var vertexShaderID: Int
            try {
                // Try to load from resource manager first (for dev environment)
                if (vertexShaderLoc == "minecraft:shaders/vertex.vsh") {
                    vertexShaderID =
                        createShader(ByteArrayInputStream(vertexShader.toByteArray()), GL20.GL_VERTEX_SHADER)
                } else {
                    vertexShaderID = createShader(
                        mc.resourceManager.getResource(ResourceLocation(vertexShaderLoc)).inputStream,
                        GL20.GL_VERTEX_SHADER
                    )
                }
            } catch (e: Exception) {
                // Fallback to embedded vertex shader
                System.err.println("Failed to load vertex shader from resource, using embedded fallback: " + e.message)
                vertexShaderID = createShader(ByteArrayInputStream(vertexShader.toByteArray()), GL20.GL_VERTEX_SHADER)
            }
            GL20.glAttachShader(program, vertexShaderID)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        GL20.glLinkProgram(program)
        val status = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS)

        check(status != 0) { "Shader failed to link!" }
        this.programID = program
    }

    fun init() {
        GL20.glUseProgram(programID)
    }

    fun unload() {
        GL20.glUseProgram(0)
    }

    fun setUniformf(name: String, vararg args: Float) {
        val loc = GL20.glGetUniformLocation(programID, name)
        if (loc == -1) {
            return
        }
        when (args.size) {
            1 -> GL20.glUniform1f(loc, args[0])
            2 -> GL20.glUniform2f(loc, args[0], args[1])
            3 -> GL20.glUniform3f(loc, args[0], args[1], args[2])
            4 -> GL20.glUniform4f(loc, args[0], args[1], args[2], args[3])
        }
    }

    fun setUniformi(name: String, vararg args: Int) {
        val loc = GL20.glGetUniformLocation(programID, name)
        if (loc == -1) {
            return
        }
        if (args.size > 1) GL20.glUniform2i(loc, args[0], args[1])
        else GL20.glUniform1i(loc, args[0])
    }

    private fun createShader(inputStream: InputStream, shaderType: Int): Int {
        val shader = GL20.glCreateShader(shaderType)
        GL20.glShaderSource(shader, readInputStream(inputStream))
        GL20.glCompileShader(shader)

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            println(GL20.glGetShaderInfoLog(shader, 4096))
            throw IllegalStateException(String.format("Shader (%s) failed to compile!", shaderType))
        }

        return shader
    }

    companion object {
        @JvmStatic
        fun drawQuads() {
            val sr = ScaledResolution(Minecraft.getMinecraft())
            val width = sr.scaledWidth_double.toFloat()
            val height = sr.scaledHeight_double.toFloat()
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glTexCoord2f(0f, 1f)
            GL11.glVertex2f(0f, 0f)
            GL11.glTexCoord2f(0f, 0f)
            GL11.glVertex2f(0f, height)
            GL11.glTexCoord2f(1f, 0f)
            GL11.glVertex2f(width, height)
            GL11.glTexCoord2f(1f, 1f)
            GL11.glVertex2f(width, 0f)
            GL11.glEnd()
        }

        @JvmStatic
        fun drawQuads(x: Float, y: Float, width: Float, height: Float) {
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glTexCoord2f(0f, 0f)
            GL11.glVertex2f(x, y)
            GL11.glTexCoord2f(0f, 1f)
            GL11.glVertex2f(x, y + height)
            GL11.glTexCoord2f(1f, 1f)
            GL11.glVertex2f(x + width, y + height)
            GL11.glTexCoord2f(1f, 0f)
            GL11.glVertex2f(x + width, y)
            GL11.glEnd()
        }

        private fun readInputStream(inputStream: InputStream): String {
            try {
                val sb = StringBuilder()
                val buffer = ByteArray(1024)
                var len: Int
                while ((inputStream.read(buffer).also { len = it }) != -1) {
                    sb.append(String(buffer, 0, len))
                }
                return sb.toString()
            } catch (e: IOException) {
                e.printStackTrace()
                return ""
            }
        }
    }
}
