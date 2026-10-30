package unfair.font.impl

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11
import java.awt.Font
import java.lang.ref.WeakReference
import java.text.Bidi
import java.util.*

/**
 * The StringCache is the public interface for rendering of all Unicode strings using OpenType fonts.
 */
class StringCache(private val colorTable: IntArray) {
    private val glyphCache: GlyphCache = GlyphCache()
    private val stringCache = WeakHashMap<Key?, Entry?>()
    private val weakRefCache = WeakHashMap<String?, Key?>()
    private val lookupKey = Key()
    private val digitGlyphs = arrayOfNulls<Array<Glyph>>(4)
    private var digitGlyphsReady = false
    private var antiAliasEnabled = false
    private val mainThread: Thread = Thread.currentThread()
    var height: Int = 0

    private class Key {
        var str: String? = null

        override fun hashCode(): Int {
            var code = 0
            val length = str!!.length
            var colorCode = false

            for (index in 0..<length) {
                var c = str!![index]
                if (c in '0'..'9' && !colorCode) {
                    c = '0'
                }
                code = (code * 31) + c.code
                colorCode = (c == '§')
            }

            return code
        }

        override fun equals(o: Any?): Boolean {
            if (o == null) {
                return false
            }

            val other = o.toString()
            val length = str!!.length

            if (length != other.length) {
                return false
            }

            var colorCode = false

            for (index in 0..<length) {
                val c1 = str!![index]
                val c2 = other[index]

                if (c1 != c2 && (c1 !in '0'..'9' || c2 < '0' || c2 > '9' || colorCode)) {
                    return false
                }
                colorCode = (c1 == '§')
            }

            return true
        }

        override fun toString(): String {
            return str!!
        }
    }

    private class Entry {
        var keyRef: WeakReference<Key?>? = null
        var advance: Int = 0
        lateinit var glyphs: Array<Glyph>
        lateinit var colors: Array<ColorCode?>
        var specialRender: Boolean = false
    }

    private class ColorCode : Comparable<Int> {
        var stringIndex: Int = 0
        var stripIndex: Int = 0
        var colorCode: Byte = 0
        var fontStyle: Byte = 0
        var renderStyle: Byte = 0

        override fun compareTo(i: Int): Int {
            return Integer.compare(stringIndex, i)
        }

        companion object {
            const val UNDERLINE: Byte = 1
            const val STRIKETHROUGH: Byte = 2
        }
    }

    private class Glyph : Comparable<Glyph> {
        var stringIndex: Int = 0
        var texture: GlyphCache.Entry? = null
        var x: Int = 0
        var y: Int = 0
        var advance: Int = 0

        override fun compareTo(o: Glyph): Int {
            return Integer.compare(stringIndex, o.stringIndex)
        }
    }

    init {
        cacheDightGlyphs()
    }

    fun setDefaultFont(font: Font?, fontSize: Int, antiAlias: Boolean) {
        glyphCache.setDefaultFont(font, fontSize, antiAlias)
        antiAliasEnabled = antiAlias
        weakRefCache.clear()
        stringCache.clear()
        cacheDightGlyphs()
    }

    private fun cacheDightGlyphs() {
        digitGlyphsReady = false
        digitGlyphs[Font.PLAIN] = cacheString("0123456789").glyphs
        digitGlyphs[Font.BOLD] = cacheString("§l0123456789").glyphs
        digitGlyphs[Font.ITALIC] = cacheString("§o0123456789").glyphs
        digitGlyphs[Font.BOLD or Font.ITALIC] = cacheString("§l§o0123456789").glyphs
        digitGlyphsReady = true
    }

    fun renderString(str: String?, startX: Float, startY: Float, initialColor: Int, shadowFlag: Boolean): Int {
        var startX = startX
        var startY = startY
        if (str == null) {
            return 0
        }

        GlStateManager.disableBlend()

        startX = Math.round(startX).toFloat()
        startY = Math.round(startY).toFloat()

        if (str.isEmpty()) {
            return 0
        }

        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)

        val entry = cacheString(str)

        startY += BASELINE_OFFSET.toFloat()

        var color = initialColor

        GlStateManager.color((color shr 16 and 0xff) / 255f, (color shr 8 and 0xff) / 255f, (color and 0xff) / 255f)

        if (antiAliasEnabled) {
            GlStateManager.enableBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        }

        val tessellator = Tessellator.getInstance()
        val worldRenderer = tessellator.worldRenderer
        worldRenderer.begin(7, DefaultVertexFormats.POSITION)
        GlStateManager.color((color shr 16 and 0xff) / 255f, (color shr 8 and 0xff) / 255f, (color and 0xff) / 255f)

        var fontStyle = Font.PLAIN

        var glyphIndex = 0
        var colorIndex = 0
        while (glyphIndex < entry.glyphs.size) {
            while (colorIndex < entry.colors.size && entry.glyphs[glyphIndex].stringIndex >= entry.colors[colorIndex]!!.stringIndex) {
                color = applyColorCode(entry.colors[colorIndex]!!.colorCode.toInt(), initialColor, shadowFlag)
                fontStyle = entry.colors[colorIndex]!!.fontStyle.toInt()
                colorIndex++
            }

            val glyph = entry.glyphs[glyphIndex]
            var texture = glyph.texture
            if (texture == null) {
                glyphIndex++
                continue
            }
            var glyphX = glyph.x

            val c = str[glyph.stringIndex]
            if (c in '0'..'9' && digitGlyphs[fontStyle] != null) {
                val oldWidth = texture.width - (texture.offsetX * 2)
                val digitGlyph = digitGlyphs[fontStyle]!![c.code - '0'.code]
                if (digitGlyph.texture != null) {
                    texture = digitGlyph.texture
                } else {
                    glyphIndex++
                    continue
                }
                val newWidth = texture!!.width - (texture.offsetX * 2)
                glyphX += (oldWidth - newWidth) shr 1
            }
            if (texture.height > this.height) {
                this.height = texture.height
            }

            GlStateManager.enableTexture2D()
            tessellator.draw()
            worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR)

            GlStateManager.bindTexture(texture.textureName)

            val x1 = startX + (glyphX - texture.offsetX) / 2.0f
            val x2 = startX + (glyphX - texture.offsetX + texture.width) / 2.0f
            val y1 = startY + (glyph.y - texture.offsetY) / 2.0f + this.height / 2f
            val y2 = startY + (glyph.y - texture.offsetY + texture.height) / 2.0f + this.height / 2f

            val a = color shr 24 and 0xff
            val r = color shr 16 and 0xff
            val g = color shr 8 and 0xff
            val b = color and 0xff

            worldRenderer.pos(x1.toDouble(), y1.toDouble(), 0.0).tex(texture.u1.toDouble(), texture.v1.toDouble())
                .color(r, g, b, a).endVertex()
            worldRenderer.pos(x1.toDouble(), y2.toDouble(), 0.0).tex(texture.u1.toDouble(), texture.v2.toDouble())
                .color(r, g, b, a).endVertex()
            worldRenderer.pos(x2.toDouble(), y2.toDouble(), 0.0).tex(texture.u2.toDouble(), texture.v2.toDouble())
                .color(r, g, b, a).endVertex()
            worldRenderer.pos(x2.toDouble(), y1.toDouble(), 0.0).tex(texture.u2.toDouble(), texture.v1.toDouble())
                .color(r, g, b, a).endVertex()
            glyphIndex++
        }

        tessellator.draw()

        if (entry.specialRender) {
            var renderStyle = 0

            GlStateManager.disableTexture2D()
            worldRenderer.begin(7, DefaultVertexFormats.POSITION)
            GlStateManager.color((color shr 16 and 0xff) / 255f, (color shr 8 and 0xff) / 255f, (color and 0xff) / 255f)
            var glyphIndex = 0
            var colorIndex = 0
            while (glyphIndex < entry.glyphs.size) {
                while (colorIndex < entry.colors.size && entry.glyphs[glyphIndex].stringIndex >= entry.colors[colorIndex]!!.stringIndex) {
                    applyColorCode(entry.colors[colorIndex]!!.colorCode.toInt(), initialColor, shadowFlag)
                    renderStyle = entry.colors[colorIndex]!!.renderStyle.toInt()
                    colorIndex++
                }

                val glyph = entry.glyphs[glyphIndex]
                val glyphSpace = glyph.advance - glyph.texture!!.width

                if ((renderStyle and ColorCode.UNDERLINE.toInt()) != 0) {
                    val x1 = startX + (glyph.x - glyphSpace) / 2.0f
                    val x2 = startX + (glyph.x + glyph.advance) / 2.0f
                    val y1 = startY + (height / 2f) + 1
                    val y2: Float = startY + (height / 2f + UNDERLINE_THICKNESS) + 1

                    worldRenderer.pos(x1.toDouble(), y1.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x1.toDouble(), y2.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x2.toDouble(), y1.toDouble(), 0.0).endVertex()
                }

                if ((renderStyle and ColorCode.STRIKETHROUGH.toInt()) != 0) {
                    val x1 = startX + (glyph.x - glyphSpace) / 2.0f
                    val x2 = startX + (glyph.x + glyph.advance) / 2.0f
                    val y1: Float = startY + (STRIKETHROUGH_OFFSET + height) / 2.0f
                    val y2: Float = startY + (STRIKETHROUGH_OFFSET + STRIKETHROUGH_THICKNESS + height) / 2.0f

                    worldRenderer.pos(x1.toDouble(), y1.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x1.toDouble(), y2.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
                    worldRenderer.pos(x2.toDouble(), y1.toDouble(), 0.0).endVertex()
                }
                glyphIndex++
            }

            tessellator.draw()
            GlStateManager.enableTexture2D()
        }

        GlStateManager.enableBlend()
        return (startX + entry.advance / 2f).toInt()
    }

    fun getStringWidth(str: String?): Int {
        if (str.isNullOrEmpty()) {
            return 0
        }

        val entry = cacheString(str)
        return entry.advance / 2
    }

    private fun sizeString(str: String?, width: Float, breakAtSpaces: Boolean): Int {
        var width = width
        if (str.isNullOrEmpty()) {
            return 0
        }

        width += width

        val glyphs = cacheString(str).glyphs

        var wsIndex = -1

        var advance = 0
        var index = 0
        while (index < glyphs.size && advance <= width) {
            if (breakAtSpaces) {
                val c = str[glyphs[index].stringIndex]
                if (c == ' ') {
                    wsIndex = index
                } else if (c == '\n') {
                    wsIndex = index
                    break
                }
            }

            advance += glyphs[index].advance
            index++
        }

        if (index < glyphs.size && wsIndex != -1 && wsIndex < index) {
            index = wsIndex
        }

        return if (index < glyphs.size) glyphs[index].stringIndex else str.length
    }

    fun sizeStringToWidth(str: String?, width: Int): Int {
        return sizeString(str, width.toFloat(), true)
    }

    fun trimStringToWidth(str: String, width: Float, reverse: Boolean): String {
        var str = str
        val length = sizeString(str, width, false)
        str = str.substring(0, length)

        if (reverse) {
            str = (StringBuilder(str)).reverse().toString()
        }

        return str
    }

    private fun applyColorCode(colorCode: Int, color: Int, shadowFlag: Boolean): Int {
        var colorCode = colorCode
        var color = color
        if (colorCode != -1) {
            colorCode = if (shadowFlag) colorCode + 16 else colorCode
            color = colorTable[colorCode] and 0xffffff or (color and -0x1000000)
        }

        Tessellator.getInstance().worldRenderer
            .color(color shr 16 and 0xff, color shr 8 and 0xff, color and 0xff, color shr 24 and 0xff)
        return color
    }

    private fun cacheString(str: String): Entry {
        val key: Key?

        var entry: Entry? = null

        if (mainThread === Thread.currentThread()) {
            lookupKey.str = str
            entry = stringCache[lookupKey]
        }

        if (entry == null) {
            val text = str.toCharArray()

            entry = Entry()
            val length = stripColorCodes(entry, str, text)

            val glyphList: MutableList<Glyph> = ArrayList<Glyph>()
            entry.advance = layoutBidiString(glyphList, text, length, entry.colors)

            entry.glyphs = glyphList.toTypedArray()

            Arrays.sort(entry.glyphs)

            var colorIndex = 0
            var shift = 0
            for (glyphIndex in entry.glyphs.indices) {
                val glyph = entry.glyphs[glyphIndex]

                while (colorIndex < entry.colors.size && glyph.stringIndex + shift >= entry.colors[colorIndex]!!.stringIndex) {
                    shift += 2
                    colorIndex++
                }
                glyph.stringIndex += shift
            }

            if (mainThread === Thread.currentThread()) {
                key = Key()
                key.str = str
                entry.keyRef = WeakReference<Key?>(key)
                stringCache[key] = entry
            }
        }

        if (mainThread === Thread.currentThread()) {
            val oldKey = entry.keyRef!!.get()
            if (oldKey != null) {
                weakRefCache[str] = oldKey
            }
            lookupKey.str = null
        }

        return entry
    }

    private fun stripColorCodes(cacheEntry: Entry, str: String, text: CharArray): Int {
        val colorList: MutableList<ColorCode?> = ArrayList<ColorCode?>()
        var start = 0
        var shift = 0
        var next: Int

        var fontStyle = Font.PLAIN.toByte()
        var renderStyle: Byte = 0
        var colorCode: Byte = -1

        while ((str.indexOf('§', start).also { next = it }) != -1 && next + 1 < str.length) {
            System.arraycopy(text, next - shift + 2, text, next - shift, text.size - next - 2)

            when (val code = "0123456789abcdefklmnor".indexOf(str[next + 1].lowercaseChar())) {
                16 -> {}
                17 -> fontStyle = (fontStyle.toInt() or Font.BOLD).toByte()
                18 -> {
                    renderStyle = (renderStyle.toInt() or ColorCode.STRIKETHROUGH.toInt()).toByte()
                    cacheEntry.specialRender = true
                }

                19 -> {
                    renderStyle = (renderStyle.toInt() or ColorCode.UNDERLINE.toInt()).toByte()
                    cacheEntry.specialRender = true
                }

                20 -> fontStyle = (fontStyle.toInt() or Font.ITALIC).toByte()
                21 -> {
                    fontStyle = Font.PLAIN.toByte()
                    renderStyle = 0
                    colorCode = -1
                }

                else -> if (code >= 0) {
                    colorCode = code.toByte()
                    fontStyle = Font.PLAIN.toByte()
                    renderStyle = 0
                }
            }

            val entry = ColorCode()
            entry.stringIndex = next
            entry.stripIndex = next - shift
            entry.colorCode = colorCode
            entry.fontStyle = fontStyle
            entry.renderStyle = renderStyle
            colorList.add(entry)

            start = next + 2
            shift += 2
        }

        cacheEntry.colors = colorList.toTypedArray()

        return text.size - shift
    }

    private fun layoutBidiString(
        glyphList: MutableList<Glyph>,
        text: CharArray,
        limit: Int,
        colors: Array<ColorCode?>
    ): Int {
        var advance = 0

        if (Bidi.requiresBidi(text, 0, limit)) {
            val bidi = Bidi(text, 0, null, 0, limit, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)

            if (bidi.isRightToLeft) {
                return layoutStyle(glyphList, text, 0, limit, Font.LAYOUT_RIGHT_TO_LEFT, advance, colors)
            } else {
                val runCount = bidi.runCount
                val levels = ByteArray(runCount)
                val ranges = arrayOfNulls<Int>(runCount)

                for (index in 0..<runCount) {
                    levels[index] = bidi.getRunLevel(index).toByte()
                    ranges[index] = index
                }
                Bidi.reorderVisually(levels, 0, ranges, 0, runCount)

                for (visualIndex in 0..<runCount) {
                    val logicalIndex: Int = ranges[visualIndex]!!
                    val layoutFlag =
                        if ((bidi.getRunLevel(logicalIndex) and 1) == 1) Font.LAYOUT_RIGHT_TO_LEFT else Font.LAYOUT_LEFT_TO_RIGHT
                    advance = layoutStyle(
                        glyphList, text, bidi.getRunStart(logicalIndex), bidi.getRunLimit(logicalIndex),
                        layoutFlag, advance, colors
                    )
                }
            }

            return advance
        } else {
            return layoutStyle(glyphList, text, 0, limit, Font.LAYOUT_LEFT_TO_RIGHT, advance, colors)
        }
    }

    private fun layoutStyle(
        glyphList: MutableList<Glyph>,
        text: CharArray,
        start: Int,
        limit: Int,
        layoutFlags: Int,
        advance: Int,
        colors: Array<ColorCode?>
    ): Int {
        var start = start
        var advance = advance
        var currentFontStyle = Font.PLAIN

        var colorIndex = Arrays.binarySearch(colors, start)

        if (colorIndex < 0) {
            colorIndex = -colorIndex - 2
        }

        while (start < limit) {
            var next = limit

            while (colorIndex >= 0 && colorIndex < (colors.size - 1) && colors[colorIndex]!!.stripIndex == colors[colorIndex + 1]!!.stripIndex) {
                colorIndex++
            }

            if (colorIndex >= 0 && colorIndex < colors.size) {
                currentFontStyle = colors[colorIndex]!!.fontStyle.toInt()
            }

            while (++colorIndex < colors.size) {
                if (colors[colorIndex]!!.fontStyle.toInt() != currentFontStyle) {
                    next = colors[colorIndex]!!.stripIndex
                    break
                }
            }

            advance = layoutString(glyphList, text, start, next, layoutFlags, advance, currentFontStyle)
            start = next
        }

        return advance
    }

    private fun layoutString(
        glyphList: MutableList<Glyph>,
        text: CharArray,
        start: Int,
        limit: Int,
        layoutFlags: Int,
        advance: Int,
        style: Int
    ): Int {
        var start = start
        var advance = advance
        if (digitGlyphsReady) {
            for (index in start..<limit) {
                if (text[index] in '0'..'9') {
                    text[index] = '0'
                }
            }
        }

        val font = glyphCache.lookupFont(text, start, limit, style)
        while (start < limit) {
            val font2 = glyphCache.lookupFont(text, start, limit, style)
            var next: Int
            if (font !== font2) {
                next = start + 1
                advance = layoutFont(glyphList, text, start, next, layoutFlags, advance, font2!!)
            } else {
                next = font!!.canDisplayUpTo(text, start, limit)

                if (next == -1) {
                    next = limit
                }

                if (next == start) {
                    next++
                }

                advance = layoutFont(glyphList, text, start, next, layoutFlags, advance, font)
            }
            start = next
        }

        return advance
    }

    private fun layoutFont(
        glyphList: MutableList<Glyph>,
        text: CharArray?,
        start: Int,
        limit: Int,
        layoutFlags: Int,
        advance: Int,
        font: Font
    ): Int {
        var advance = advance
        if (mainThread === Thread.currentThread()) {
            glyphCache.cacheGlyphs(font, text, start, limit, layoutFlags)
        }

        val vector = glyphCache.layoutGlyphVector(font, text, start, limit, layoutFlags)

        var glyph: Glyph? = null
        val numGlyphs = vector.numGlyphs
        for (index in 0..<numGlyphs) {
            val position = vector.getGlyphPixelBounds(index, null, advance.toFloat(), 0f).location

            if (glyph != null) {
                glyph.advance = position.x - glyph.x
            }

            glyph = Glyph()
            glyph.stringIndex = start + vector.getGlyphCharIndex(index)
            glyph.texture = glyphCache.lookupGlyph(font, vector.getGlyphCode(index))
            glyph.x = position.x
            glyph.y = position.y
            glyphList.add(glyph)
        }

        advance += vector.getGlyphPosition(numGlyphs).x.toInt()
        if (glyph != null) {
            glyph.advance = advance - glyph.x
        }

        return advance
    }

    companion object {
        private const val BASELINE_OFFSET = 0
        private const val UNDERLINE_OFFSET = 1
        private const val UNDERLINE_THICKNESS = 1
        private const val STRIKETHROUGH_OFFSET = -6
        private const val STRIKETHROUGH_THICKNESS = 2
    }
}
