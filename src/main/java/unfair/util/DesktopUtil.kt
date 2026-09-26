package unfair.util

import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Opens URLs and folders across Windows / macOS / Linux.
 *
 * AWT [Desktop] is attempted first (best integration on Windows and most
 * mainstream Linux desktops), then falls back to the platform's native
 * launcher command — `open` on macOS, `xdg-open` on Linux, `explorer` on
 * Windows. The fallbacks matter on minimal Linux setups where AWT reports
 * BROWSE/OPEN as unsupported.
 */
object DesktopUtil {

    /** Opens [url] in the system browser. Returns false if every attempt failed. */
    @JvmStatic
    fun openUrl(url: String): Boolean {
        if (desktopBrowse(url)) return true
        val command = when {
            Platform.isMac -> listOf("open", url)
            Platform.isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> listOf("xdg-open", url)
        }
        return runCommand(command)
    }

    /** Opens [file] (typically a folder) in the system file manager. Returns false on failure. */
    @JvmStatic
    fun openFile(file: File): Boolean {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
                return true
            }
        } catch (t: Throwable) {
            // Fall through to native commands.
        }
        val path = file.absolutePath
        val command = when {
            Platform.isMac -> listOf("open", path)
            Platform.isWindows -> listOf("explorer", path)
            else -> listOf("xdg-open", path)
        }
        return runCommand(command)
    }

    private fun desktopBrowse(url: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            false
        }
    }

    private fun runCommand(command: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            // Drain the output so the child process cannot block on a full pipe.
            process.inputStream.close()
            process.waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }
}
