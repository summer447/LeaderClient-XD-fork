package unfair.util.shader

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import unfair.util.RenderUtil
import unfair.util.shader.ShaderUtils.Companion.drawQuads
import java.awt.Color
import kotlin.math.max

object RoundedUtils {
    private var roundedShader: ShaderUtils? = null
    private var roundedOutlineShader: ShaderUtils? = null
    private var roundedTexturedShader: ShaderUtils? = null
    private var roundedGradientShader: ShaderUtils? = null
    private var roundedRectRiseShader: ShaderUtils? = null

    private fun initShaders() {
        if (roundedShader == null) {
            try {
                roundedShader = ShaderUtils("roundedRect")
            } catch (e: Exception) {
                System.err.println("Failed to initialize roundedShader: " + e.message)
            }
        }
        if (roundedOutlineShader == null) {
            try {
                roundedOutlineShader = ShaderUtils("roundRectOutline")
            } catch (e: Exception) {
                System.err.println("Failed to initialize roundedOutlineShader: " + e.message)
            }
        }
        if (roundedTexturedShader == null) {
            try {
                roundedTexturedShader = ShaderUtils("roundRectTexture")
            } catch (e: Exception) {
                System.err.println("Failed to initialize roundedTexturedShader: " + e.message)
            }
        }
        if (roundedGradientShader == null) {
            try {
                roundedGradientShader = ShaderUtils("roundedRectGradient")
            } catch (e: Exception) {
                System.err.println("Failed to initialize roundedGradientShader: " + e.message)
            }
        }
        if (roundedRectRiseShader == null) {
            try {
                roundedRectRiseShader = ShaderUtils("roundedRectRise")
            } catch (e: Exception) {
                System.err.println("Failed to initialize roundedRectRiseShader: " + e.message)
            }
        }
    }


    @JvmStatic
    fun drawRound(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        drawRound(x, y, width, height, radius, false, color)
    }

    @JvmStatic
    fun drawGradientHorizontal(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        left: Color,
        right: Color
    ) {
        drawGradientRound(x, y, width, height, radius, left, left, right, right)
    }

    @JvmStatic
    fun drawGradientVertical(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        top: Color,
        bottom: Color
    ) {
        drawGradientRound(x, y, width, height, radius, bottom, top, bottom, top)
    }

    fun drawGradientCornerLR(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        topLeft: Color,
        bottomRight: Color
    ) {
        val mixedColor = RenderUtil.interpolateColorC(topLeft, bottomRight, .5f)
        drawGradientRound(x, y, width, height, radius, mixedColor, topLeft, bottomRight, mixedColor)
    }

    fun drawGradientCornerRL(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        bottomLeft: Color,
        topRight: Color
    ) {
        val mixedColor = RenderUtil.interpolateColorC(topRight, bottomLeft, .5f)
        drawGradientRound(x, y, width, height, radius, bottomLeft, mixedColor, mixedColor, topRight)
    }

    @JvmStatic
    fun drawRound(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        drawRound(x, y, width, height, radius, false, color)
    }

    fun drawRound(x: Float, y: Float, width: Float, height: Float, radius: Float, blur: Boolean, color: Int) {
        initShaders()
        if (roundedShader == null) return

        RenderUtil.resetColor()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        RenderUtil.setAlphaLimit(0f)

        roundedShader!!.init()
        setupRoundedRectUniforms(x, y, width, height, radius, roundedShader!!)
        roundedShader!!.setUniformi("blur", if (blur) 1 else 0)
        roundedShader!!.setUniformf("color", getRed(color), getGreen(color), getBlue(color), getAlpha(color))

        drawQuads(x - 1, y - 1, width + 2, height + 2)
        roundedShader!!.unload()
        GlStateManager.disableBlend()
    }

    fun drawGradientRound(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        bottomLeft: Color,
        topLeft: Color,
        bottomRight: Color,
        topRight: Color
    ) {
        initShaders()
        if (roundedGradientShader == null) return

        RenderUtil.setAlphaLimit(0f)
        RenderUtil.resetColor()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        roundedGradientShader!!.init()
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader!!)
        roundedGradientShader!!.setUniformf(
            "color1",
            topLeft.red / 255f,
            topLeft.green / 255f,
            topLeft.blue / 255f,
            topLeft.alpha / 255f
        )
        roundedGradientShader!!.setUniformf(
            "color2",
            bottomLeft.red / 255f,
            bottomLeft.green / 255f,
            bottomLeft.blue / 255f,
            bottomLeft.alpha / 255f
        )
        roundedGradientShader!!.setUniformf(
            "color3",
            topRight.red / 255f,
            topRight.green / 255f,
            topRight.blue / 255f,
            topRight.alpha / 255f
        )
        roundedGradientShader!!.setUniformf(
            "color4",
            bottomRight.red / 255f,
            bottomRight.green / 255f,
            bottomRight.blue / 255f,
            bottomRight.alpha / 255f
        )
        drawQuads(x - 1, y - 1, width + 2, height + 2)
        roundedGradientShader!!.unload()
        GlStateManager.disableBlend()
    }

    fun drawGradientRound(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        bottomLeft: Int,
        topLeft: Int,
        bottomRight: Int,
        topRight: Int
    ) {
        initShaders()
        if (roundedGradientShader == null) return

        RenderUtil.setAlphaLimit(0f)
        RenderUtil.resetColor()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        roundedGradientShader!!.init()
        setupRoundedRectUniforms(x, y, width, height, radius, roundedGradientShader!!)

        roundedGradientShader!!.setUniformf(
            "color1",
            getRed(topLeft),
            getGreen(topLeft),
            getBlue(topLeft),
            getAlpha(topLeft)
        )
        roundedGradientShader!!.setUniformf(
            "color2",
            getRed(bottomLeft),
            getGreen(bottomLeft),
            getBlue(bottomLeft),
            getAlpha(bottomLeft)
        )
        roundedGradientShader!!.setUniformf(
            "color3",
            getRed(topRight),
            getGreen(topRight),
            getBlue(topRight),
            getAlpha(topRight)
        )
        roundedGradientShader!!.setUniformf(
            "color4",
            getRed(bottomRight),
            getGreen(bottomRight),
            getBlue(bottomRight),
            getAlpha(bottomRight)
        )

        drawQuads(x - 1, y - 1, width + 2, height + 2)
        roundedGradientShader!!.unload()
        GlStateManager.disableBlend()
    }

    @JvmStatic
    fun drawRound(x: Float, y: Float, width: Float, height: Float, radius: Float, blur: Boolean, color: Color) {
        initShaders()
        if (roundedShader == null) return

        RenderUtil.resetColor()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        RenderUtil.setAlphaLimit(0f)
        roundedShader!!.init()

        setupRoundedRectUniforms(x, y, width, height, radius, roundedShader!!)
        roundedShader!!.setUniformi("blur", if (blur) 1 else 0)
        roundedShader!!.setUniformf(
            "color",
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )

        drawQuads(x - 1, y - 1, width + 2, height + 2)
        roundedShader!!.unload()
        GlStateManager.disableBlend()
    }


    @JvmStatic
    fun drawRoundOutline(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        outlineThickness: Float,
        color: Color,
        outlineColor: Color
    ) {
        initShaders()
        if (roundedOutlineShader == null) return

        RenderUtil.resetColor()
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        RenderUtil.setAlphaLimit(0f)
        roundedOutlineShader!!.init()

        val sr = ScaledResolution(Minecraft.getMinecraft())
        setupRoundedRectUniforms(x, y, width, height, radius, roundedOutlineShader!!)
        roundedOutlineShader!!.setUniformf("outlineThickness", outlineThickness * sr.scaleFactor)
        roundedOutlineShader!!.setUniformf(
            "color",
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )
        roundedOutlineShader!!.setUniformf(
            "outlineColor",
            outlineColor.red / 255f,
            outlineColor.green / 255f,
            outlineColor.blue / 255f,
            outlineColor.alpha / 255f
        )


        drawQuads(
            x - (2 + outlineThickness),
            y - (2 + outlineThickness),
            width + (4 + outlineThickness * 2),
            height + (4 + outlineThickness * 2)
        )
        roundedOutlineShader!!.unload()
        GlStateManager.disableBlend()
    }


    @JvmStatic
    fun drawRoundTextured(x: Float, y: Float, width: Float, height: Float, radius: Float, alpha: Float) {
        initShaders()
        if (roundedTexturedShader == null) return

        RenderUtil.resetColor()
        RenderUtil.setAlphaLimit(0f)
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        roundedTexturedShader!!.init()
        roundedTexturedShader!!.setUniformi("textureIn", 0)
        setupRoundedRectUniforms(x, y, width, height, radius, roundedTexturedShader!!)
        roundedTexturedShader!!.setUniformf("alpha", alpha)
        drawQuads(x - 1, y - 1, width + 2, height + 2)
        roundedTexturedShader!!.unload()
        GlStateManager.disableBlend()
    }

    private fun setupRoundedRectUniforms(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        roundedTexturedShader: ShaderUtils
    ) {
        val sr = ScaledResolution(Minecraft.getMinecraft())
        roundedTexturedShader.setUniformf(
            "location", x * sr.scaleFactor,
            (Minecraft.getMinecraft().displayHeight - (height * sr.scaleFactor)) - (y * sr.scaleFactor)
        )
        roundedTexturedShader.setUniformf("rectSize", width * sr.scaleFactor, height * sr.scaleFactor)
        roundedTexturedShader.setUniformf("radius", radius * sr.scaleFactor)
    }

    @JvmStatic
    fun drawRoundedRectRise(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        leftTop: Boolean,
        rightTop: Boolean,
        rightBottom: Boolean,
        leftBottom: Boolean
    ) {
        initShaders()
        if (roundedRectRiseShader == null) return

        GL11.glPushMatrix()
        GlStateManager.pushAttrib()
        val programId = roundedRectRiseShader!!.programID
        GL20.glUseProgram(programId)
        roundedRectRiseShader!!.setUniformf("u_size", width, height)
        roundedRectRiseShader!!.setUniformf("u_radius", radius)
        roundedRectRiseShader!!.setUniformf("u_color", getRed(color), getGreen(color), getBlue(color), getAlpha(color))
        roundedRectRiseShader!!.setUniformf(
            "u_edges",
            if (leftTop) 1.0f else 0.0f,
            if (rightTop) 1.0f else 0.0f,
            if (rightBottom) 1.0f else 0.0f,
            if (leftBottom) 1.0f else 0.0f
        )
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        drawQuads(x, y, width, height)
        GlStateManager.disableBlend()
        GL20.glUseProgram(0)
        GlStateManager.popAttrib()
        GL11.glPopMatrix()
    }

    fun drawRoundedRectRise(x: Double, y: Double, width: Double, height: Double, radius: Double, color: Int) {
        drawRoundedRectRise(
            x.toFloat(),
            y.toFloat(),
            width.toFloat(),
            height.toFloat(),
            radius.toFloat(),
            color,
            true,
            rightTop = true,
            rightBottom = true,
            leftBottom = true
        )
    }

    /**
     * Draw a rounded rectangle with individual corner radius control.
     * Pass 0 for a radius to make that corner square, or a positive value to round it.
     * Useful for seamless connections between UI elements.
     * 
     * Example: top corners square (0), bottom corners rounded (6):
     * drawRound(x, y, w, h, 0, 0, 6, 6, color);
     */
    fun drawRound(
        x: Float, y: Float, width: Float, height: Float,
        radiusTL: Float, radiusTR: Float, radiusBR: Float, radiusBL: Float,
        color: Color
    ) {
        // Use the max radius for the shader SDF, control individual corners via edges
        val maxRadius = max(max(radiusTL, radiusTR), max(radiusBR, radiusBL))
        val lt = radiusTL > 0 // top-left
        val rt = radiusTR > 0 // top-right
        val rb = radiusBR > 0 // bottom-right
        val lb = radiusBL > 0 // bottom-left

        initShaders()
        if (roundedRectRiseShader == null) return

        GL11.glPushMatrix()
        GlStateManager.pushAttrib()
        val programId = roundedRectRiseShader!!.programID
        GL20.glUseProgram(programId)
        roundedRectRiseShader!!.setUniformf("u_size", width, height)
        roundedRectRiseShader!!.setUniformf("u_radius", maxRadius)
        roundedRectRiseShader!!.setUniformf(
            "u_color",
            color.red / 255f, color.green / 255f,
            color.blue / 255f, color.alpha / 255f
        )

        // Shader u_edges: x=bottomLeft, y=bottomRight, z=topRight, w=topLeft
        // when edge==0 → square, edge==1 → rounded
        roundedRectRiseShader!!.setUniformf(
            "u_edges",
            if (lb) 1.0f else 0.0f,  // u_edges.x = bottom-left
            if (rb) 1.0f else 0.0f,  // u_edges.y = bottom-right
            if (rt) 1.0f else 0.0f,  // u_edges.z = top-right
            if (lt) 1.0f else 0.0f
        ) // u_edges.w = top-left
        GlStateManager.enableBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        drawQuads(x, y, width, height)
        GlStateManager.disableBlend()
        GL20.glUseProgram(0)
        GlStateManager.popAttrib()
        GL11.glPopMatrix()
    }

    private fun getRed(color: Int): Float {
        return (color shr 16 and 0xFF) / 255.0f
    }

    private fun getGreen(color: Int): Float {
        return (color shr 8 and 0xFF) / 255.0f
    }

    private fun getBlue(color: Int): Float {
        return (color and 0xFF) / 255.0f
    }

    private fun getAlpha(color: Int): Float {
        return (color shr 24 and 0xFF) / 255.0f
    }
}
