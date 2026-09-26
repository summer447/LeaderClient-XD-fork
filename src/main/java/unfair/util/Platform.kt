package unfair.util

/**
 * Cross-platform OS detection, based on the `os.name` system property.
 *
 * Use this instead of sprinkling raw `System.getProperty("os.name")` checks:
 * native integrations (DWM caption, wallpaper discovery, desktop launchers)
 * branch on these flags.
 */
object Platform {

    private val osName: String = System.getProperty("os.name")?.lowercase() ?: ""

    @JvmStatic
    val isWindows: Boolean = osName.startsWith("windows")

    @JvmStatic
    val isMac: Boolean = osName.contains("mac") || osName.contains("darwin")

    @JvmStatic
    val isLinux: Boolean = osName.contains("linux") || osName.contains("nix") || osName.contains("nux")
}
