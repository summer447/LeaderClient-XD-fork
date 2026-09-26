package unfair.init

import org.spongepowered.asm.lib.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class FMLLoadingPlugin : IMixinConfigPlugin {
    private var mixinPackage: String? = null
    private var mixins: MutableList<String?>? = null

    override fun onLoad(mixinPackage: String) {
        this.mixinPackage = mixinPackage
        mixinPlugins.add(this)
    }

    fun getBaseUrlForClassUrl(classUrl: URL): URL {
        val string = classUrl.toString()
        if (classUrl.protocol == "jar") {
            try {
                return URL(string.substring(4).split("!".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])
            } catch (e: MalformedURLException) {
                throw RuntimeException(e)
            }
        } else if (string.endsWith(".class")) {
            try {
                return URL(
                    string.replace("\\", "/")
                        .replace(this.javaClass.getCanonicalName().replace(".", "/") + ".class", "")
                )
            } catch (e: MalformedURLException) {
                throw RuntimeException(e)
            }
        } else {
            return classUrl
        }
    }

    fun getMixinPackage(): String {
        return this.mixinPackage!!
    }

    val mixinBaseDir: String
        get() = this.mixinPackage!!.replace(".", "/")

    fun tryAddMixinClass(className: String) {
        val norm = (if (className.endsWith(".class")) className.substring(
            0,
            className.length - ".class".length
        ) else className).replace("\\", "/").replace("/", ".")
        if (norm.startsWith(this.getMixinPackage() + ".") && !norm.endsWith(".")) {
            this.mixins!!.add(norm.substring(this.getMixinPackage().length + 1))
        }
    }

    override fun getMixins(): MutableList<String?>? {
        if (this.mixins != null) {
            return this.mixins
        } else {
            this.mixins = ArrayList()
            val classUrl = this.javaClass.getProtectionDomain().codeSource.location
            println("Found classes at $classUrl")
            val file: Path?
            try {
                file = Paths.get(this.getBaseUrlForClassUrl(classUrl).toURI())
            } catch (e: URISyntaxException) {
                throw RuntimeException(e)
            }
            println("Base directory found at $file")
            if (Files.isDirectory(file)) {
                this.walkDir(file)
            } else {
                this.walkJar(file)
            }
            println("Found mixins: " + this.mixins)
            return this.mixins
        }
    }

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun walkDir(classRoot: Path) {
        println("Trying to find mixins from directory")
        try {
            Files.walk(classRoot.resolve(this.mixinBaseDir)).use { classes ->
                classes.map { it: Path? -> classRoot.relativize(it).toString() }
                    .forEach { className: String? -> this.tryAddMixinClass(className!!) }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun walkJar(file: Path) {
        println("Trying to find mixins from jar file")
        try {
            ZipInputStream(Files.newInputStream(file)).use { zis ->
                var next: ZipEntry?
                while ((zis.getNextEntry().also { next = it }) != null) {
                    this.tryAddMixinClass(next!!.getName())
                    zis.closeEntry()
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun preApply(
        targetClassName: String?,
        targetClass: ClassNode?,
        mixinClassName: String?,
        mixinInfo: IMixinInfo?
    ) {
    }

    override fun postApply(
        targetClassName: String?,
        targetClass: ClassNode?,
        mixinClassName: String?,
        mixinInfo: IMixinInfo?
    ) {
    }

    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String?, mixinClassName: String?): Boolean {
        return true
    }

    override fun acceptTargets(myTargets: MutableSet<String?>?, otherTargets: MutableSet<String?>?) {
    }

    companion object {
        val mixinPlugins: MutableList<FMLLoadingPlugin?> = ArrayList()
    }
}
