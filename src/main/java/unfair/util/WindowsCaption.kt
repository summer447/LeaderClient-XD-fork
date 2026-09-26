package unfair.util

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import org.lwjgl.opengl.Display
import unfair.ui.MaterialYou

/**
 * Applies Material You colors to the native Windows window caption via DWM.
 * Uses only core JNA (bundled with Minecraft); the jna-platform jar is not on
 * the compile classpath.
 *
 * - DWMWA_USE_IMMERSIVE_DARK_MODE (20): dark title bar (Win10 1903+ / Win11)
 * - DWMWA_CAPTION_COLOR (35): caption background = scheme.surfaceContainer (Win11)
 * - DWMWA_TEXT_COLOR (36): caption text = scheme.onSurface (Win11)
 *
 * Fullscreen toggle: LWJGL2 destroys and recreates the window during the
 * transition; a window carrying a custom caption color can hang DWM when it is
 * destroyed. MixinMinecraft therefore calls [reset] BEFORE toggleFullscreen and
 * [apply] AFTER it, re-attaching the attributes to the freshly created HWND.
 *
 * Every entry point is a no-op off Windows: Linux/macOS have no equivalent of
 * the DWM caption attributes, and dwmapi is never loaded there.
 */
object WindowsCaption {

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    /** Passing this color to CAPTION_COLOR/TEXT_COLOR restores the system default. */
    private const val DWMWA_COLOR_DEFAULT = -1 // 0xFFFFFFFF

    // Must be public: JNA implements this interface with a dynamic proxy.
    // StdCallLibrary already carries the stdcall function mapper, so no options map is needed.
    interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: Pointer, dwAttribute: Int, pvAttribute: IntByReference, cbAttribute: Int): Int
    }

    private val dwmapi: DwmApi? by lazy {
        try {
            Native.loadLibrary("dwmapi", DwmApi::class.java) as DwmApi
        } catch (t: Throwable) {
            println("[WindowsCaption] failed to load dwmapi: $t")
            null
        }
    }

    /** ARGB -> COLORREF (0x00BBGGRR) used by DWM caption attributes. */
    private fun toColorRef(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return b shl 16 or (g shl 8) or r
    }

    private fun getGameHwnd(): Pointer? {
        if (!Display.isCreated()) return null
        return try {
            val impl = Display::class.java.getDeclaredMethod("getImplementation").apply { isAccessible = true }.invoke(null)
            val hwnd = impl.javaClass.getDeclaredMethod("getHwnd").apply { isAccessible = true }.invoke(impl) as Long
            Pointer(hwnd)
        } catch (t: Throwable) {
            println("[WindowsCaption] failed to resolve game hwnd: $t")
            null
        }
    }

    /** Tints the game window caption with the active Material You scheme. No-op off Windows or on failure. */
    @JvmStatic
    fun apply() {
        if (!Platform.isWindows) return
        val dwm = dwmapi ?: return
        val hwnd = getGameHwnd() ?: return
        val dark = if (MaterialYou.darkTheme) 1 else 0
        val results = intArrayOf(
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(dark), 4),
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, IntByReference(toColorRef(MaterialYou.scheme.surfaceContainer)), 4),
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, IntByReference(toColorRef(MaterialYou.scheme.onSurface)), 4)
        )
        println("[WindowsCaption] hwnd=$hwnd dark=${results[0]} caption=${results[1]} text=${results[2]}")
    }

    /**
     * Restores the system-default caption before the window is destroyed
     * (fullscreen toggle), avoiding the DWM hang on Win11.
     */
    @JvmStatic
    fun reset() {
        if (!Platform.isWindows) return
        val dwm = dwmapi ?: return
        val hwnd = getGameHwnd() ?: return
        val def = IntByReference(DWMWA_COLOR_DEFAULT)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, def, 4)
        dwm.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, def, 4)
    }
}
