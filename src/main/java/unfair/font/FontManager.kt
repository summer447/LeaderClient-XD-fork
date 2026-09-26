package unfair.font

import unfair.font.impl.UFontRenderer

class FontManager {
    var s12: UFontRenderer? = null
    var s14: UFontRenderer? = null
    var s15: UFontRenderer? = null
    var s16: UFontRenderer? = null
    var s18: UFontRenderer? = null
    var s20: UFontRenderer? = null
    var s22: UFontRenderer? = null
    var s24: UFontRenderer? = null
    var s28: UFontRenderer? = null
    var s36: UFontRenderer? = null
    var s40: UFontRenderer? = null

    @JvmField
    var icon16: UFontRenderer? = null
    var icon20: UFontRenderer? = null
    var icon24: UFontRenderer? = null

    private val fontCache: MutableMap<String?, MutableMap<Int?, UFontRenderer>> =
        HashMap()

    fun load() {
        s12 = getFont("NotoSansSC-Regular", 12)
        s14 = getFont("NotoSansSC-Regular", 14)
        s15 = getFont("NotoSansSC-Regular", 15)
        s16 = getFont("NotoSansSC-Regular", 16)
        s18 = getFont("NotoSansSC-Regular", 18)
        s20 = getFont("NotoSansSC-Regular", 20)
        s22 = getFont("NotoSansSC-Regular", 22)
        s24 = getFont("NotoSansSC-Regular", 24)
        s28 = getFont("NotoSansSC-Regular", 28)
        s36 = getFont("NotoSansSC-Regular", 36)
        s40 = getFont("NotoSansSC-Regular", 40)

        icon16 = getFont("icon", 16)
        icon20 = getFont("icon", 20)
        icon24 = getFont("icon", 24)
    }

    fun getFont(size: Int): UFontRenderer {
        return getFont("NotoSansSC-Regular", size)
    }

    fun getFont(fontName: String?, size: Int): UFontRenderer {
        val sizeMap = fontCache.computeIfAbsent(fontName) { _: String? -> HashMap() }
        return sizeMap.computeIfAbsent(size) { _: Int? -> UFontRenderer(fontName, size) }
    }
}