package unfair.util

import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

object KeyBindUtil {
    @JvmStatic
    fun getKeyName(keyCode: Int): String? {
        return if (keyCode < 0) Mouse.getButtonName(keyCode + 100) else Keyboard.getKeyName(keyCode)
    }

    @JvmStatic
    fun isKeyDown(keyCode: Int): Boolean {
        return if (keyCode < 0) Mouse.isButtonDown(keyCode + 100) else Keyboard.isKeyDown(keyCode)
    }

    @JvmStatic
    fun updateKeyState(keyCode: Int) {
        setKeyBindState(keyCode, if (keyCode < 0) Mouse.isButtonDown(keyCode + 100) else Keyboard.isKeyDown(keyCode))
    }

    @JvmStatic
    fun setKeyBindState(keyCode: Int, pressed: Boolean) {
        KeyBinding.setKeyBindState(keyCode, pressed)
    }

    @JvmStatic
    fun pressKeyOnce(keyCode: Int) {
        KeyBinding.onTick(keyCode)
    }
}
