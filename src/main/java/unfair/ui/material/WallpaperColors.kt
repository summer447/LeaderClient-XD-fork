package unfair.ui.material

import unfair.ui.material.color.quantize.QuantizerCelebi
import unfair.ui.material.color.score.Score
import unfair.util.Platform
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.net.URLDecoder
import javax.imageio.ImageIO

/**
 * Material You dynamic color source.
 *
 * Extracts the seed color from the desktop wallpaper using the same
 * pipeline as Android 12+: quantize (Celebi/Wu+Wsmeans) -> score -> top color.
 *
 * Wallpaper discovery is platform-specific:
 * - Windows: %APPDATA%\Microsoft\Windows\Themes (TranscodedWallpaper), registry
 * - macOS: Finder AppleScript (primary display)
 * - Linux: gsettings (GNOME/Cinnamon/MATE), xfconf (XFCE), Plasma config (KDE)
 */
object WallpaperColors {

    fun extractSeedColor(fallback: Int): Int {
        return try {
            val file = findWallpaperFile() ?: return fallback
            val image = ImageIO.read(file) ?: return fallback
            val pixels = downscaleToPixels(image, 128)
            val quantized = QuantizerCelebi.quantize(pixels, 128)
            Score.score(quantized, 1).firstOrNull() ?: fallback
        } catch (t: Throwable) {
            fallback
        }
    }

    private fun findWallpaperFile(): File? = when {
        Platform.isWindows -> findWindowsWallpaper()
        Platform.isMac -> findMacWallpaper()
        Platform.isLinux -> findLinuxWallpaper()
        else -> null
    }

    // ---------------------------------------------------------------- Windows

    private fun findWindowsWallpaper(): File? {
        val appData = System.getenv("APPDATA")
        // TranscodedWallpaper always mirrors the current wallpaper (incl. slideshow)
        if (appData != null) {
            val transcoded = File(appData, "Microsoft/Windows/Themes/TranscodedWallpaper")
            if (transcoded.isFile && transcoded.length() > 0) return transcoded
        }
        // Registry: HKCU\Control Panel\Desktop / Wallpaper
        readRegistryWallpaper()?.let { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        // Slideshow cache
        if (appData != null) {
            val cachedDir = File(appData, "Microsoft/Windows/Themes/CachedFiles")
            cachedDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }?.let { return it }
        }
        return null
    }

    private fun readRegistryWallpaper(): String? {
        return try {
            val process = ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop", "/v", "Wallpaper")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            Regex("""Wallpaper\s+REG_SZ\s+(\S.*)""").find(output)
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------------ macOS

    /**
     * Asks Finder for the desktop picture of the primary display. Per-space /
     * per-display wallpapers are not distinguished; the primary display is
     * representative for the seed color.
     */
    private fun findMacWallpaper(): File? {
        val path = runCommand(
            listOf(
                "osascript",
                "-e",
                "tell application \"Finder\" to get POSIX path of (desktop picture as alias)"
            )
        )?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return File(path).takeIf { it.isFile }
    }

    // ------------------------------------------------------------------ Linux

    private fun findLinuxWallpaper(): File? {
        findGnomeWallpaper()?.let { return it }
        gsettingsFile("org.cinnamon.desktop.background", "picture-uri")?.let { return it }
        gsettingsFile("org.mate.desktop.background", "picture-filename")?.let { return it }
        findXfceWallpaper()?.let { return it }
        findKdeWallpaper()?.let { return it }
        return null
    }

    /** GNOME (and Budgie, which shares the schema). Prefers the dark-mode picture. */
    private fun findGnomeWallpaper(): File? {
        for (key in listOf("picture-uri-dark", "picture-uri")) {
            val raw = runCommand(listOf("gsettings", "get", "org.gnome.desktop.background", key)) ?: continue
            resolveWallpaperValue(raw)?.let { return it }
        }
        return null
    }

    private fun gsettingsFile(schema: String, key: String): File? {
        val raw = runCommand(listOf("gsettings", "get", schema, key)) ?: return null
        return resolveWallpaperValue(raw)
    }

    /** XFCE stores the backdrop per monitor/workspace; pick the first configured image. */
    private fun findXfceWallpaper(): File? {
        val listing = runCommand(listOf("xfconf-query", "-c", "xfce4-desktop", "-l")) ?: return null
        val property = listing.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith("last-image") || it.endsWith("image-path") }
            ?: return null
        val value = runCommand(listOf("xfconf-query", "-c", "xfce4-desktop", "-p", property)) ?: return null
        return resolveWallpaperValue(value)
    }

    /** KDE Plasma keeps the wallpaper Image= entry in its desktop applets config. */
    private fun findKdeWallpaper(): File? {
        val config = File(System.getProperty("user.home"), ".config/plasma-org.kde.plasma.desktop-appletsrc")
        if (!config.isFile) return null
        return try {
            config.useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Image=")) resolveWallpaperValue(trimmed.substringAfter("Image=")) else null
                }.firstOrNull()
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * gsettings/xfconf values may be quoted plain paths, `file://` URIs, or an
     * XML slideshow descriptor referencing the actual images.
     */
    private fun resolveWallpaperValue(raw: String): File? {
        val file = toFile(raw) ?: return null
        if (file.extension.equals("xml", ignoreCase = true)) {
            return resolveSlideshowXml(file)
        }
        return file.takeIf { it.isFile }
    }

    private fun resolveSlideshowXml(xml: File): File? {
        return try {
            val text = xml.readText()
            Regex("""<file>([^<]+)</file>""")
                .findAll(text)
                .mapNotNull { toFile(it.groupValues[1]) }
                .firstOrNull { it.isFile && it.extension.lowercase() !in setOf("xml") }
        } catch (t: Throwable) {
            null
        }
    }

    /** Accepts `'file:///path'`, quoted strings and plain POSIX paths. */
    private fun toFile(value: String): File? {
        var path = value.trim().trim('"', '\'').trim()
        if (path.isEmpty()) return null
        if (path.startsWith("file://")) {
            path = try {
                URLDecoder.decode(path.substring(7), "UTF-8")
            } catch (t: Throwable) {
                path.substring(7)
            }
        }
        return File(path)
    }

    private fun runCommand(command: List<String>): String? {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() == 0) output else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun downscaleToPixels(image: BufferedImage, maxSize: Int): IntArray {
        val scale = minOf(1f, maxSize.toFloat() / maxOf(image.width, image.height))
        val sw = maxOf(1, (image.width * scale).toInt())
        val sh = maxOf(1, (image.height * scale).toInt())
        val scaled = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.drawImage(image.getScaledInstance(sw, sh, Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()
        return scaled.getRGB(0, 0, sw, sh, null, 0, sw)
    }
}
