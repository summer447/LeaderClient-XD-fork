package unfair.property

import unfair.module.Module

class PropertyManager {
    @JvmField
    var properties =
        LinkedHashMap<Class<*>?, ArrayList<Property<*>>?>()

    @JvmField
    var instanceProperties =
        LinkedHashMap<Module, ArrayList<Property<*>>>()

    fun getProperty(module: Module, string: String): Property<*>? {
        for (property in getProperties(module) ?: return null) {
            if (property.name.replace("-", "").equals(string.replace("-", ""), ignoreCase = true)) {
                return property
            }
        }
        return null
    }

    fun getProperties(module: Module): ArrayList<Property<*>>? {
        instanceProperties[module]?.let { return it }
        return properties[module.javaClass]
    }

    fun registerInstanceProperties(module: Module, list: ArrayList<Property<*>>) {
        for (property in list) {
            property.setOwner(module)
        }
        instanceProperties[module] = list
    }
}
