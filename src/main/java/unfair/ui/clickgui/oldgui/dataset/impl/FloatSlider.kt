package unfair.ui.clickgui.oldgui.dataset.impl

import unfair.enums.ChatColors
import unfair.property.properties.FloatProperty
import unfair.ui.clickgui.oldgui.dataset.Slider

class FloatSlider(private val property: FloatProperty) : Slider() {
    override fun getInput(): Double {
        return property.value.toDouble()
    }

    override fun getMin(): Double {
        return property.minimum.toDouble()
    }

    override fun getMax(): Double {
        return property.maximum.toDouble()
    }

    override fun setValue(value: Double) {
        property.setValue(value.toFloat())
    }

    override fun getName(): String {
        return property.name.replace("-", " ")
    }

    override fun getValueString(): String {
        return ChatColors.formatColor(property.formatValue())
    }

    override fun getIncrement(): Double {
        return 0.1
    }

    override fun isVisible(): Boolean {
        return property.isVisible
    }
}
