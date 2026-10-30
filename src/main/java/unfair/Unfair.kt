package unfair

import org.lwjgl.opengl.Display
import unfair.command.CommandManager
import unfair.config.Config
import unfair.event.EventManager
import unfair.font.FontManager
import unfair.management.*
import unfair.module.ModuleManager
import unfair.property.Property
import unfair.property.PropertyManager
import unfair.ui.MaterialYou
import kotlin.concurrent.thread

class Unfair {
    init {
        this.init()
    }

    fun init() {
        propertyManager = PropertyManager()
        playerStateManager = PlayerStateManager()
        targetManager = TargetManager()

        EventManager.autoRegister("unfair.management")
        rotationManager = EventManager.getInstance(RotationManager::class.java)
        floatManager = EventManager.getInstance(FloatManager::class.java)
        blinkManager = EventManager.getInstance(BlinkManager::class.java)
        delayManager = EventManager.getInstance(DelayManager::class.java)
        lagManager = EventManager.getInstance(LagManager::class.java)

        moduleManager = ModuleManager()
        commandManager = CommandManager()
        fontManager = FontManager()
        fontManager!!.load()
        for (module in moduleManager!!.modules.values) {
            val properties: ArrayList<Property<*>> = ArrayList<Property<*>?>() as ArrayList<Property<*>>
            for (field in module.javaClass.getDeclaredFields()) {
                field.setAccessible(true)
                val obj: Any?
                try {
                    obj = field.get(module)
                } catch (e: IllegalAccessException) {
                    throw RuntimeException(e)
                }
                if (obj is Property<*>) {
                    obj.setOwner(module)
                    properties.add(obj)
                }
            }
            propertyManager!!.properties[module.javaClass] = properties
        }
        val config = Config("default", true)
        if (config.file.exists()) {
            config.load()
        }
        if (targetManager!!.file.exists()) {
            targetManager!!.load()
        }
        Runtime.getRuntime().addShutdownHook(Thread { config.save() })
        thread(isDaemon = true) { MaterialYou.prewarm() }

        if (Display.isCreated()) {
            Display.setTitle("$CLIENT_NAME $version - $MINECRAFT_VERSION")
            unfair.util.WindowsCaption.apply()
        }
    }

    companion object {
        @JvmField
        var clientName: String = "&l[&b&lLeader-XD&f&l]&r "
        var CLIENT_NAME: String = "Leader-XD"
        var MINECRAFT_VERSION: String = "1.8.9"
        var version : String = "Meow"
        @JvmField
        var rotationManager: RotationManager? = null
        @JvmField
        var floatManager: FloatManager? = null
        @JvmField
        var blinkManager: BlinkManager? = null
        @JvmField
        var delayManager: DelayManager? = null
        @JvmField
        var lagManager: LagManager? = null
        @JvmField
        var playerStateManager: PlayerStateManager? = null
        @JvmField
        var targetManager: TargetManager? = null
        @JvmField
        var propertyManager: PropertyManager? = null
        @JvmField
        var moduleManager: ModuleManager? = null
        @JvmField
        var commandManager: CommandManager? = null
        @JvmField
        var fontManager: FontManager? = null
    }
}
