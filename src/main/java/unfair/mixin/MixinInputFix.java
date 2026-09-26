package unfair.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * InputFix: vanilla only forwards keyTyped when a key is actually pressed
 * (Keyboard.getEventKeyState), which drops IME-composed characters
 * (eventKey == 0 with a valid character). Forward those too so CJK input
 * works in text fields.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiScreen.class)
public abstract class MixinInputFix {
    @Shadow
    public Minecraft mc;

    @Shadow
    protected abstract void keyTyped(char typedChar, int keyCode) throws IOException;

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    protected void onHandleKeyboardInput(CallbackInfo ci) throws IOException {
        ci.cancel();
        char eventCharacter = Keyboard.getEventCharacter();
        int eventKey = Keyboard.getEventKey();
        boolean keyPressed = Keyboard.getEventKeyState();
        boolean imeComposedChar = eventKey == 0 && eventCharacter >= ' ';
        if (keyPressed || imeComposedChar) {
            this.keyTyped(eventCharacter, eventKey);
        }
        this.mc.dispatchKeypresses();
    }
}
