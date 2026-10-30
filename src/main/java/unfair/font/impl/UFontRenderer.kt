package unfair.font.impl

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import unfair.Unfair
import java.awt.Font
import kotlin.math.max

class UFontRenderer(name: String?, private val size: Int) : FontRenderer(
    Minecraft.getMinecraft().gameSettings,
    ResourceLocation("textures/font/ascii.png"),
    Minecraft.getMinecraft().textureManager,
    false
) {
    private val fontName: String? = name
    private val stringCache: StringCache

    init {
        val font: Font? = loadFont(name, size)
        stringCache = StringCache(COLOR_CODE)
        stringCache.setDefaultFont(font, size, true)
    }

    override fun drawStringWithShadow(text: String?, x: Float, y: Float, color: Int): Int {
        drawString(text, x, y, color, true)
        return getStringWidth(text)
    }

    fun trimStringToWidth(text: String, width: Float): String {
        return trimString(text, width, false)
    }

    fun trimString(text: String, width: Float, reverse: Boolean): String {
        val stringbuilder = StringBuilder()
        for (c in text.toCharArray()) {
            if (getStringWidth(stringbuilder.toString()) < width) stringbuilder.append(c)
            else break
        }
        return stringbuilder.toString()
    }

    fun drawString(text: String?, x: Float, y: Int, color: Int): Int {
        return this.drawString(text, x, y.toFloat(), color, false)
    }

    fun drawString(text: String?, x: Float, y: Float, color: Int): Int {
        this.drawString(text, x, y, color, false)
        return getStringWidth(text)
    }

    fun drawStringCapableWithEmoji(text: String, x: Float, y: Float, color: Int): Int {
        val chars = text.toCharArray()
        var lastCut = 0
        var xOffset = x
        for (i in chars.indices) {
            if (isEmojiCharacter(text.codePointAt(i))) {
                xOffset += this.drawString(text.substring(0, i), xOffset, y, color, false).toFloat()
                this.drawString(chars[i].toString(), xOffset, y, color, false)
                xOffset += this.getStringWidth(chars[i].toString()).toFloat()
                lastCut = i + 1
            }
        }
        this.drawString(text.substring(lastCut), xOffset, y, color, false)
        return getStringWidth(text)
    }

    override fun drawString(text: String?, x: Float, y: Float, color: Int, dropShadow: Boolean): Int {
        val densityScale = this.densityScale
        if (densityScale > 1.0f) {
            return drawHighDensityString(text, x, y, color, dropShadow, densityScale)
        }
        return drawStringInternal(text, x, y, color, dropShadow)
    }

    private fun drawHighDensityString(
        text: String?,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        densityScale: Float
    ): Int {
        val renderer = getDensityRenderer(densityScale)
        if (renderer === this) {
            return drawStringInternal(text, x, y, color, dropShadow)
        }

        val actualDensityScale = renderer.size / size.toFloat()
        val inverseScale = 1.0f / actualDensityScale
        GL11.glPushMatrix()
        GL11.glScalef(inverseScale, inverseScale, 1.0f)
        try {
            val result = renderer.drawStringInternal(
                text,
                x * actualDensityScale,
                y * actualDensityScale,
                color,
                dropShadow,
                actualDensityScale * 0.5f
            )
            return Math.round(result * inverseScale)
        } finally {
            GL11.glPopMatrix()
        }
    }

    private fun drawStringInternal(
        text: String?,
        x: Float,
        y: Float,
        color: Int,
        dropShadow: Boolean,
        shadowOffset: Float = 0.5f
    ): Int {
        if (dropShadow) {
            val alpha = (color shr 24) and 0xFF
            if (alpha > 50) {
                val shadowColor = (20 shl 16) or (20 shl 8) or 20 or (alpha shl 24)
                stringCache.renderString(text, x + shadowOffset, y + shadowOffset, shadowColor, true)
            }
        }
        return stringCache.renderString(text, x, y, color, false)
    }

    override fun getStringWidth(text: String?): Int {
        val densityScale = this.densityScale
        if (densityScale > 1.0f) {
            val renderer = getDensityRenderer(densityScale)
            if (renderer !== this) {
                val actualDensityScale = renderer.size / size.toFloat()
                return Math.round(renderer.stringCache.getStringWidth(text) / actualDensityScale)
            }
        }
        return stringCache.getStringWidth(text)
    }

    fun drawCenteredString(text: String?, x: Float, y: Float, color: Int) {
        drawString(text, x - stringCache.getStringWidth(text) / 2f, y, color, false)
    }

    val height: Int
        get() {
            val densityScale = this.densityScale
            if (densityScale > 1.0f) {
                val renderer = getDensityRenderer(densityScale)
                if (renderer !== this) {
                    val actualDensityScale = renderer.size / size.toFloat()
                    return Math.round(renderer.stringCache.height / 2f / actualDensityScale)
                }
            }
            return stringCache.height / 2
        }

    private val densityScale: Float
        get() {
            try {
                var guiScale = Minecraft.getMinecraft().gameSettings.guiScale
                if (guiScale <= 0) guiScale = 1
                return max(1.0f, guiScale.toFloat())
            } catch (e: Exception) {
                return 1.0f
            }
        }

    private fun getDensityRenderer(densityScale: Float): UFontRenderer {
        val scaledSize = max(size, Math.round(size * densityScale))
        if (scaledSize == size) {
            return this
        }
        return Unfair.fontManager!!.getFont(fontName, scaledSize)
    }

    fun drawStringCapableWithEmojiWithShadow(text: String, x: Float, y: Float, color: Int): Float {
        var text = text
        val sbs = arrayOf(
            "\uD83C\uDF89",
            "\uD83C\uDF81",
            "\uD83D\uDC79",
            "\uD83C\uDFC0",
            "⚽",
            "\uD83C\uDF6D",
            "\uD83C\uDF20",
            "\uD83D\uDC7E",
            "\uD83D\uDC0D",
            "\uD83D\uDD2E",
            "\uD83D\uDC7D",
            "\uD83D\uDCA3",
            "\uD83C\uDF6B",
            "\uD83C\uDF82"
        )
        for (sb in sbs) {
            text = text.replace(sb.toRegex(), "")
        }
        return drawStringWithShadow(text, x, y, color).toFloat()
    }

    companion object {
        private val COLOR_CODE = IntArray(32)
        private val FONT_CACHE: MutableMap<String?, Font?> = HashMap()

        init {
            for (i in 0..31) {
                val j = (i shr 3 and 1) * 85
                var k = (i shr 2 and 1) * 170 + j
                var l = (i shr 1 and 1) * 170 + j
                var i1 = (i and 1) * 170 + j
                if (i == 6) {
                    k += 85
                }
                if (i >= 16) {
                    k /= 4
                    l /= 4
                    i1 /= 4
                }
                COLOR_CODE[i] = (k and 255) shl 16 or ((l and 255) shl 8) or (i1 and 255)
            }
        }

        private fun loadFont(name: String?, size: Int): Font? {
            var base: Font? = FONT_CACHE[name]
            if (base != null) {
                return base.deriveFont(Font.PLAIN, size.toFloat())
            }
            try {
                val `is` = UFontRenderer::class.java.getResourceAsStream("/assets/unfair/fonts/$name.ttf")
                if (`is` != null) {
                    base = Font.createFont(Font.TRUETYPE_FONT, `is`)
                    `is`.close()
                    FONT_CACHE.put(name, base)
                    return base.deriveFont(Font.PLAIN, size.toFloat())
                }
            } catch (_: Exception) {
            }
            return Font("Arial", Font.PLAIN, size)
        }

        fun isEmojiCharacter(codePoint: Int): Boolean {
            return (codePoint == 0x0) ||
                    (codePoint == 0x9) ||
                    (codePoint == 0xA) ||
                    (codePoint == 0xD) ||
                    (codePoint >= 0x20 && codePoint <= 0xD7FF) || ((codePoint >= 0xE000) && (codePoint <= 0xFFFD))
        }
    }
}