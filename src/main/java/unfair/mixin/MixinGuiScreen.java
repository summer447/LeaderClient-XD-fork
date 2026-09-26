package unfair.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.ui.MaterialYou;

/**
 * Replaces the vanilla dirt background ({@code GuiScreen.drawBackground}) with a solid
 * Material You {@code surfaceContainer} fill, keeping the in-world dark gradient
 * ({@code drawWorldBackground} when {@code mc.theWorld != null}) untouched.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void onDrawBackground(int tint, CallbackInfo ci) {
        int color = MaterialYou.getScheme().getSurfaceContainer();
        net.minecraft.client.gui.Gui.drawRect(0, 0, ((GuiScreen) (Object) this).width, ((GuiScreen) (Object) this).height, color);
        ci.cancel();
    }
}
