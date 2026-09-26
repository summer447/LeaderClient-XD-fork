package unfair.ui.clickgui.oldgui.dataset.impl

import unfair.enums.ChatColors
import unfair.property.properties.PercentProperty
import unfair.ui.clickgui.oldgui.dataset.Slider

class PercentageSlider(private val property: PercentProperty) : Slider() {
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
        property.setValue(value.toInt())
    }

    override fun getName(): String {
        return property.name.replace("-", " ")
    }

    override fun getValueString(): String {
        return ChatColors.formatColor(property.formatValue())
    }

    override fun getIncrement(): Double {
        return 1.0
    }

    override fun isVisible(): Boolean {
        return property.isVisible
    }
}
