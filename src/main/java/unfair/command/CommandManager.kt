package unfair.command

import net.minecraft.network.play.client.C01PacketChatMessage
import unfair.Unfair
import unfair.event.EventManager
import unfair.event.EventTarget
import unfair.event.types.EventType
import unfair.event.types.Priority
import unfair.events.PacketEvent
import unfair.util.ChatUtil
import java.io.File
import java.util.Comparator
import java.util.jar.JarFile

class CommandManager {
    @JvmField
    var commands: ArrayList<Command> = ArrayList<Command>()

    init {
        EventManager.register(this)
        autoRegisterCommands()
    }

    private fun autoRegisterCommands() {
        val commandClasses = scanPackageForCommands("unfair.command.commands")
        commandClasses.sortWith(Comparator.comparing { it.simpleName })

        for (clazz in commandClasses) {
            try {
                commands.add(EventManager.getOrCreate(clazz) as Command)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun scanPackageForCommands(packageName: String): MutableList<Class<out Command>> {
        val result = ArrayList<Class<out Command>>()
        val path = packageName.replace('.', '/')

        try {
            val resources = Thread.currentThread().getContextClassLoader().getResources(path)
            while (resources.hasMoreElements()) {
                val url = resources.nextElement()
                if (url.protocol == "jar") {
                    val jarPath = url.path.substring(5, url.path.indexOf("!"))
                    JarFile(jarPath).use { jar ->
                        val entries = jar.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith(path) && name.endsWith(".class")) {
                                val className = name.replace('/', '.').substring(0, name.length - 6)
                                addIfCommand(className, result)
                            }
                        }
                    }
                } else if (url.protocol == "file") {
                    val directory = File(url.toURI())
                    if (directory.exists() && directory.isDirectory) {
                        scanDirectoryForCommands(directory, packageName, result)
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return result
    }

    private fun scanDirectoryForCommands(
        directory: File,
        packageName: String,
        result: MutableList<Class<out Command>>
    ) {
        val files = directory.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                scanDirectoryForCommands(file, "$packageName.${file.name}", result)
            } else if (file.name.endsWith(".class")) {
                val className = packageName + "." + file.name.substring(0, file.name.length - 6)
                addIfCommand(className, result)
            }
        }
    }

    private fun addIfCommand(className: String, result: MutableList<Class<out Command>>) {
        try {
            val clazz = Class.forName(className)
            if (Command::class.java.isAssignableFrom(clazz) &&
                !clazz.isInterface &&
                !java.lang.reflect.Modifier.isAbstract(clazz.modifiers)
            ) {
                @Suppress("UNCHECKED_CAST")
                result.add(clazz as Class<out Command>)
            }
        } catch (ignored: ClassNotFoundException) {
        }
    }

    fun handleCommand(string: String) {
        val params = listOf<String?>(
            *string.substring(1).trim { it <= ' ' }.split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        )
        val arrayList = ArrayList<String?>(params)
        if (params.get(0)!!.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sUnknown command&r", Unfair.clientName).replace("&", "§"))
        } else {
            for (command in Unfair.commandManager!!.commands) {
                for (name in command.names) {
                    if (params.get(0).equals(name, ignoreCase = true)) {
                        command.runCommand(arrayList)
                        return
                    }
                }
            }
            ChatUtil.sendFormatted(
                String.format("%sUnknown command (&o%s&r)&r", Unfair.clientName, params.get(0)).replace("&", "§")
            )
        }
    }

    fun isTypingCommand(string: String?): Boolean {
        if (string == null || string.length < 2) {
            return false
        } else {
            return string.get(0) == '.' && Character.isLetterOrDigit(string.get(1))
        }
    }

    @EventTarget(Priority.HIGHEST)
    fun onPacket(event: PacketEvent) {
        if (event.getType() == EventType.SEND && event.getPacket() is C01PacketChatMessage) {
            val msg = (event.getPacket() as C01PacketChatMessage).getMessage()
            if (this.isTypingCommand(msg)) {
                event.setCancelled(true)
                this.handleCommand(msg)
            }
        }
    }
}
