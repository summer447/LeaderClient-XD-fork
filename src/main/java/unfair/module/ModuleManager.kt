package unfair.module

import unfair.event.EventManager
import unfair.event.EventTarget
import unfair.event.types.EventType
import unfair.events.TickEvent
import unfair.util.SoundUtil.playSound
import java.io.File
import java.lang.reflect.Modifier
import java.net.URL
import java.util.*
import java.util.function.Function
import java.util.jar.JarFile

class ModuleManager {
    @JvmField
    val modules: LinkedHashMap<Class<*>?, Module> = LinkedHashMap<Class<*>?, Module>()

    private var sound = false

    init {
        EventManager.register(this)
        autoRegisterModules()
    }

    private fun autoRegisterModules() {
        val moduleClasses: MutableList<Class<out Module?>?> = ArrayList<Class<out Module?>?>()

        for (category in Category.entries) {
            if (category.packageName == null) continue
            moduleClasses.addAll(scanPackageForModules(category.packageName))
        }

        moduleClasses.sortWith(Comparator.comparing<Class<out Module?>?, String?>(Function { obj: Class<out Module?>? -> obj!!.getSimpleName() }))

        for (clazz in moduleClasses) {
            try {
                val module = EventManager.getOrCreate(clazz) as Module?
                modules[clazz] = module!!
            } catch (_: Exception) {
            }
        }
    }

    private fun scanPackageForModules(packageName: String): MutableList<Class<out Module?>?> {
        val result: MutableList<Class<out Module?>?> = ArrayList<Class<out Module?>?>()
        val path = packageName.replace('.', '/')

        try {
            val resources = Thread.currentThread().getContextClassLoader().getResources(path)
            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                if (url.protocol == "jar") {
                    // Round-trip through URL->URI->File so spaces/non-ASCII
                    // characters in the path (percent-encoded in the jar URL)
                    // are decoded before opening the file.
                    val nestedUrl = url.path.substring(0, url.path.indexOf("!"))
                    JarFile(File(URL(nestedUrl).toURI())).use { jar ->
                        val entries = jar.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.getName()
                            if (name.startsWith(path) && name.endsWith(".class")) {
                                val className = name.replace('/', '.').substring(0, name.length - 6)
                                try {
                                    val clazz = Class.forName(className)
                                    if (Module::class.java.isAssignableFrom(clazz) && !clazz.isInterface && !Modifier.isAbstract(
                                            clazz.modifiers
                                        )
                                    ) {
                                        result.add(clazz as Class<out Module?>)
                                    }
                                } catch (_: ClassNotFoundException) {
                                }
                            }
                        }
                    }
                } else if (url.protocol == "file") {
                    val directory = File(url.toURI())
                    if (directory.exists() && directory.isDirectory()) {
                        scanDirectoryForModules(directory, packageName, result)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return result
    }

    private fun scanDirectoryForModules(
        directory: File,
        packageName: String?,
        result: MutableList<Class<out Module?>?>
    ) {
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory()) {
                scanDirectoryForModules(file, packageName + "." + file.getName(), result)
            } else if (file.getName().endsWith(".class")) {
                val className = packageName + "." + file.getName().substring(0, file.getName().length - 6)
                try {
                    val clazz = Class.forName(className)
                    if (Module::class.java.isAssignableFrom(clazz) && !clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)) {
                        result.add(clazz as Class<out Module?>)
                    }
                } catch (_: ClassNotFoundException) {
                }
            }
        }
    }

    fun getModule(string: String?): Module? {
        return this.modules.values.stream().filter { mD: Module? -> mD!!.getName().equals(string, ignoreCase = true) }
            .findFirst().orElse(null)
    }

    fun getModule(clazz: Class<*>?): Module? {
        return this.modules.get(clazz)
    }

    fun getModulesByCategory(category: Category?): MutableList<Module?> {
        val categoryModules: MutableList<Module?> = ArrayList<Module?>()
        for (module in modules.values) {
            if (module.getCategory() == category) {
                categoryModules.add(module)
            }
        }
        categoryModules.sortWith(Comparator.comparing<Module?, String?>(Function { m: Module? ->
                    m!!.getName().lowercase(
                        Locale.getDefault()
                    )
                }))
        return categoryModules
    }

    fun playSound() {
        this.sound = true
    }

    @EventTarget
    fun onTick(event: TickEvent) {
        if (event.type == EventType.PRE) {
            if (this.sound) {
                this.sound = false
                playSound("random.click")
            }
        }
    }
}
