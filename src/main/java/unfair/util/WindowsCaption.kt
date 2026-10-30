package unfair.util

import org.lwjgl.opengl.Display
import unfair.ui.MaterialYou

/**
 * Applies Material You colors to the native Windows window caption via DWM.
 *
 * JNA is NOT bundled in the mod jar: it is expected to come from Minecraft's
 * own libraries (1.8.9 ships net.java.dev.jna:jna:3.4.0). On launchers or
 * machines where that jar is missing/corrupted, or has been quarantined by AV,
 * every JNA type is confined to the nested [Dwm] object. Its bytecode — and
 * therefore its bytecode verification — references no JNA class, so a
 * NoClassDefFoundError/ExceptionInInitializerError surfaces only inside the
 * try/Throwable guards here and the feature silently degrades to a no-op
 * instead of crashing game startup.
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

    /** ARGB -> COLORREF (0x00BBGGRR) used by DWM caption attributes. */
    private fun toColorRef(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return b shl 16 or (g shl 8) or r
    }

    /** Tints the game window caption with the active Material You scheme. No-op off Windows or on failure. */
    @JvmStatic
    fun apply() {
        if (!Platform.isWindows) return
        try {
            val dark = if (MaterialYou.darkTheme) 1 else 0
            val results = intArrayOf(
                Dwm.setAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE, dark),
                Dwm.setAttribute(DWMWA_CAPTION_COLOR, toColorRef(MaterialYou.scheme.surfaceContainer)),
                Dwm.setAttribute(DWMWA_TEXT_COLOR, toColorRef(MaterialYou.scheme.onSurface))
            )
            println("[WindowsCaption] dark=${results[0]} caption=${results[1]} text=${results[2]}")
        } catch (t: Throwable) {
            println("[WindowsCaption] unavailable, caption customization skipped: $t")
        }
    }

    /**
     * Restores the system-default caption before the window is destroyed
     * (fullscreen toggle), avoiding the DWM hang on Win11.
     */
    @JvmStatic
    fun reset() {
        if (!Platform.isWindows) return
        try {
            Dwm.resetAttribute(DWMWA_CAPTION_COLOR)
            Dwm.resetAttribute(DWMWA_TEXT_COLOR)
        } catch (t: Throwable) {
            println("[WindowsCaption] reset skipped: $t")
        }
    }

    /**
     * The ONLY place that touches JNA. Public methods of [WindowsCaption] use
     * plain [Int]s only, so a missing JNA jar can never break their callers.
     */
    private object Dwm {

        // Must be public: JNA implements this interface with a dynamic proxy.
        // StdCallLibrary already carries the stdcall function mapper, so no options map is needed.
        interface DwmApi : com.sun.jna.win32.StdCallLibrary {
            fun DwmSetWindowAttribute(
                hwnd: com.sun.jna.Pointer,
                dwAttribute: Int,
                pvAttribute: com.sun.jna.ptr.IntByReference,
                cbAttribute: Int
            ): Int
        }

        private val api: DwmApi? by lazy {
            try {
                com.sun.jna.Native.loadLibrary("dwmapi", DwmApi::class.java) as DwmApi
            } catch (t: Throwable) {
                println("[WindowsCaption] failed to load dwmapi: $t")
                null
            }
        }

        private fun getGameHwnd(): com.sun.jna.Pointer? {
            if (!Display.isCreated()) return null
            return try {
                val impl = Display::class.java.getDeclaredMethod("getImplementation").apply { isAccessible = true }.invoke(null)
                val hwnd = impl.javaClass.getDeclaredMethod("getHwnd").apply { isAccessible = true }.invoke(impl) as Long
                com.sun.jna.Pointer(hwnd)
            } catch (t: Throwable) {
                println("[WindowsCaption] failed to resolve game hwnd: $t")
                null
            }
        }

        /** @return the HRESULT from DWM, or -1 when the API/window is unavailable. */
        fun setAttribute(attribute: Int, value: Int): Int {
            val dwm = api ?: return -1
            val hwnd = getGameHwnd() ?: return -1
            return dwm.DwmSetWindowAttribute(hwnd, attribute, com.sun.jna.ptr.IntByReference(value), 4)
        }

        fun resetAttribute(attribute: Int) {
            val dwm = api ?: return
            val hwnd = getGameHwnd() ?: return
            dwm.DwmSetWindowAttribute(hwnd, attribute, com.sun.jna.ptr.IntByReference(DWMWA_COLOR_DEFAULT), 4)
        }
    }
}
