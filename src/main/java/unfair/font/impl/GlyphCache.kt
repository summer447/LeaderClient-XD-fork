package unfair.font.impl

import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * The GlyphCache class is responsible for caching pre-rendered images of every glyph using OpenGL textures.
 */
class GlyphCache {
    private var fontSize = 18
    private var antiAliasEnabled = false

    private var stringImage: BufferedImage? = null
    private var stringGraphics: Graphics2D? = null

    private val glyphCacheImage = BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
    private val glyphCacheGraphics: Graphics2D = glyphCacheImage.createGraphics()
    private val fontRenderContext: FontRenderContext? = glyphCacheGraphics.fontRenderContext
    private val imageData = IntArray(TEXTURE_WIDTH * TEXTURE_HEIGHT)
    private val imageBuffer: IntBuffer =
        ByteBuffer.allocateDirect(4 * TEXTURE_WIDTH * TEXTURE_HEIGHT).order(ByteOrder.BIG_ENDIAN).asIntBuffer()
    private val singleIntBuffer: IntBuffer = GLAllocation.createDirectIntBuffer(1)
    private val allFonts: MutableList<Font?> =
        Arrays.asList<Font?>(*GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts)
    private val usedFonts: MutableList<Font> = ArrayList<Font>()

    private var textureName = 0
    private val fontCache = LinkedHashMap<Font?, Int?>()
    private val glyphCache = LinkedHashMap<Long?, Entry?>()

    private var cachePosX: Int = GLYPH_BORDER
    private var cachePosY: Int = GLYPH_BORDER
    private var cacheLineHeight = 0

    class Entry {
        @JvmField
        var textureName: Int = 0
        @JvmField
        var width: Int = 0
        @JvmField
        var height: Int = 0
        @JvmField
        var offsetX: Int = 0
        @JvmField
        var offsetY: Int = 0
        @JvmField
        var u1: Float = 0f
        @JvmField
        var v1: Float = 0f
        @JvmField
        var u2: Float = 0f
        @JvmField
        var v2: Float = 0f
    }

    init {
        glyphCacheGraphics.background = BACK_COLOR
        glyphCacheGraphics.composite = AlphaComposite.Src
        allocateGlyphCacheTexture()
        allocateStringImage(STRING_WIDTH, STRING_HEIGHT)
        GraphicsEnvironment.getLocalGraphicsEnvironment().preferLocaleFonts()
    }

    fun setDefaultFont(font: Font?, size: Int, antiAlias: Boolean) {
        usedFonts.clear()
        usedFonts.add(font!!)
        fontSize = size
        antiAliasEnabled = antiAlias
        setRenderingHints()
        resetCache()
    }

    fun layoutGlyphVector(font: Font, text: CharArray?, start: Int, limit: Int, layoutFlags: Int): GlyphVector {
        if (!fontCache.containsKey(font)) {
            fontCache.put(font, fontCache.size)
        }
        return font.layoutGlyphVector(fontRenderContext, text, start, limit, layoutFlags)
    }

    fun lookupFont(text: CharArray?, start: Int, limit: Int, style: Int): Font? {
        var iterator = usedFonts.iterator()
        while (iterator.hasNext()) {
            val font = iterator.next()
            if (font.canDisplayUpTo(text, start, limit) != start) {
                return font.deriveFont(style, fontSize.toFloat())
            }
        }

        iterator = allFonts.iterator() as MutableIterator<Font>
        while (iterator.hasNext()) {
            val font = iterator.next()
            if (font.canDisplayUpTo(text, start, limit) != start) {
                usedFonts.add(font)
                return font.deriveFont(style, fontSize.toFloat())
            }
        }

        val font = usedFonts.get(0)
        return font.deriveFont(style, fontSize.toFloat())
    }

    fun lookupGlyph(font: Font?, glyphCode: Int): Entry? {
        val fontKey = fontCache.get(font)!!.toLong() shl 32
        return glyphCache.get(fontKey or glyphCode.toLong())
    }

    private fun resetCache() {
        glyphCache.clear()
        fontCache.clear()
        cachePosX = GLYPH_BORDER
        cachePosY = GLYPH_BORDER
        cacheLineHeight = 0
        allocateGlyphCacheTexture()
    }

    fun cacheGlyphs(font: Font, text: CharArray?, start: Int, limit: Int, layoutFlags: Int) {
        val vector = layoutGlyphVector(font, text, start, limit, layoutFlags)
        var vectorBounds: Rectangle? = null
        val fontKey = fontCache.get(font)!!.toLong() shl 32

        val numGlyphs = vector.numGlyphs
        var dirty: Rectangle? = null
        var vectorRendered = false

        for (index in 0..<numGlyphs) {
            val glyphCode = vector.getGlyphCode(index)
            if (glyphCache.containsKey(fontKey or glyphCode.toLong())) {
                continue
            }

            if (!vectorRendered) {
                vectorRendered = true

                for (i in 0..<numGlyphs) {
                    val pos = vector.getGlyphPosition(i)
                    pos.setLocation(pos.x + (8 + GLYPH_PAD * 2) * i, pos.y)
                    vector.setGlyphPosition(i, pos)
                }

                vectorBounds = vector.getPixelBounds(fontRenderContext, 0f, 0f)
                val pad: Int = GLYPH_PAD
                vectorBounds = Rectangle(
                    vectorBounds.x - pad,
                    vectorBounds.y - pad,
                    vectorBounds.width + pad * 2,
                    vectorBounds.height + pad * 2
                )

                if (stringImage == null) return

                if (vectorBounds.width > stringImage!!.width || vectorBounds.height > stringImage!!.height) {
                    val width = max(vectorBounds.width, stringImage!!.width)
                    val height = max(vectorBounds.height, stringImage!!.height)
                    allocateStringImage(width, height)
                }

                stringGraphics!!.clearRect(0, 0, vectorBounds.width, vectorBounds.height)
                stringGraphics!!.drawGlyphVector(vector, -vectorBounds.x.toFloat(), -vectorBounds.y.toFloat())
            }

            var rect = vector.getGlyphPixelBounds(index, null, -vectorBounds!!.x.toFloat(), -vectorBounds.y.toFloat())
            val pad: Int = GLYPH_PAD
            val srcW = stringImage!!.width
            val srcH = stringImage!!.height
            val x1 = max(rect.x - pad, 0)
            val y1 = max(rect.y - pad, 0)
            val x2 = min(rect.x + rect.width + pad, srcW)
            val y2 = min(rect.y + rect.height + pad, srcH)
            rect = Rectangle(x1, y1, max(1, x2 - x1), max(1, y2 - y1))

            if (cachePosX + rect.width + GLYPH_BORDER > TEXTURE_WIDTH) {
                cachePosX = GLYPH_BORDER
                cachePosY += cacheLineHeight + GLYPH_BORDER
                cacheLineHeight = 0
            }

            if (cachePosY + rect.height + GLYPH_BORDER > TEXTURE_HEIGHT) {
                updateTexture(dirty)
                dirty = null
                allocateGlyphCacheTexture()
                cachePosX = GLYPH_BORDER
                cachePosY = cachePosX
                cacheLineHeight = 0
            }

            if (rect.height > cacheLineHeight) {
                cacheLineHeight = rect.height
            }

            glyphCacheGraphics.drawImage(
                stringImage,
                cachePosX, cachePosY, cachePosX + rect.width, cachePosY + rect.height,
                rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, null
            )

            rect.setLocation(cachePosX, cachePosY)

            val entry = Entry()
            entry.textureName = textureName
            entry.width = rect.width
            entry.height = rect.height
            entry.offsetX = pad
            entry.offsetY = pad * 2
            entry.u1 = (rect.x.toFloat()) / TEXTURE_WIDTH
            entry.v1 = (rect.y.toFloat()) / TEXTURE_HEIGHT
            entry.u2 = ((rect.x + rect.width).toFloat()) / TEXTURE_WIDTH
            entry.v2 = ((rect.y + rect.height).toFloat()) / TEXTURE_HEIGHT

            glyphCache.put(fontKey or glyphCode.toLong(), entry)

            if (dirty == null) {
                dirty = Rectangle(cachePosX, cachePosY, rect.width, rect.height)
            } else {
                dirty.add(rect)
            }

            cachePosX += rect.width + GLYPH_BORDER
        }

        updateTexture(dirty)
    }

    private fun updateTexture(dirty: Rectangle?) {
        if (dirty != null) {
            updateImageBuffer(dirty.x, dirty.y, dirty.width, dirty.height)
            GlStateManager.bindTexture(textureName)
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0, dirty.x, dirty.y, dirty.width, dirty.height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer
            )
        }
    }

    private fun allocateStringImage(width: Int, height: Int) {
        stringImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        stringGraphics = stringImage!!.createGraphics()
        stringGraphics!!.color = BACK_COLOR
        stringGraphics!!.fillRect(0, 0, width, height)
        stringGraphics!!.background = BACK_COLOR
        setRenderingHints()
        stringGraphics!!.color = Color.WHITE
    }

    private fun setRenderingHints() {
        stringGraphics!!.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (antiAliasEnabled) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF
        )
        stringGraphics!!.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            if (antiAliasEnabled) RenderingHints.VALUE_TEXT_ANTIALIAS_ON else RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
        )
        stringGraphics!!.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS,
            if (fontSize <= 14) RenderingHints.VALUE_FRACTIONALMETRICS_OFF else RenderingHints.VALUE_FRACTIONALMETRICS_ON
        )
    }

    private fun allocateGlyphCacheTexture() {
        glyphCacheGraphics.clearRect(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)
        singleIntBuffer.clear()
        GL11.glGenTextures(singleIntBuffer)
        textureName = singleIntBuffer.get(0)
        updateImageBuffer(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT)
        GlStateManager.bindTexture(textureName)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA8, TEXTURE_WIDTH, TEXTURE_HEIGHT, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        val filter = if (fontSize <= 14) GL11.GL_NEAREST else GL11.GL_LINEAR
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter)
    }

    private fun updateImageBuffer(x: Int, y: Int, width: Int, height: Int) {
        glyphCacheImage.getRGB(x, y, width, height, imageData, 0, width)
        for (i in 0..<width * height) {
            val color = imageData[i]
            imageData[i] = (color shl 8) or (color ushr 24)
        }
        imageBuffer.clear()
        imageBuffer.put(imageData)
        imageBuffer.flip()
    }

    fun readImageToBuffer(bufferedImage: BufferedImage): ByteBuffer {
        val rgbArray = bufferedImage.getRGB(
            0,
            0,
            bufferedImage.width,
            bufferedImage.height,
            null,
            0,
            bufferedImage.width
        )
        val byteBuffer = ByteBuffer.allocateDirect(4 * rgbArray.size)
        for (rgb in rgbArray) {
            byteBuffer.putInt(rgb shl 8 or (rgb shr 24 and 255))
        }
        byteBuffer.flip()
        return byteBuffer
    }

    fun loadGlTexture(bufferedImage: BufferedImage): Int {
        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, bufferedImage.width, bufferedImage.height,
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageBuffer
        )
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        return textureId
    }

    companion object {
        private const val TEXTURE_WIDTH = 256
        private const val TEXTURE_HEIGHT = 256
        private const val STRING_WIDTH = 256
        private const val STRING_HEIGHT = 64
        private const val GLYPH_BORDER = 1
        private const val GLYPH_PAD = 1
        private val BACK_COLOR = Color(255, 255, 255, 0)
    }
}
