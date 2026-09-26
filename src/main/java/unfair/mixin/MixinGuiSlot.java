package unfair.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.ui.MaterialYou;

/**
 * Replaces the vanilla dirt-texture top/bottom bars ({@code overlayBackground})
 * and the list-body background ({@code drawContainerBackground}) with a solid
 * Material You {@code surfaceContainer} fill.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {
    @Shadow
    protected int left;
    @Shadow
    protected int right;
    @Shadow
    protected int top;
    @Shadow
    protected int bottom;

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void onOverlayBackground(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
        int color = MaterialYou.getScheme().getSurfaceContainer();
        Gui.drawRect(this.left, startY, this.right, endY, color);
        ci.cancel();
    }

    // Forge-added method, no obfuscation mapping exists
    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void onDrawContainerBackground(Tessellator tessellator, CallbackInfo ci) {
        int color = MaterialYou.getScheme().getSurfaceContainer();
        Gui.drawRect(this.left, this.top, this.right, this.bottom, color);
        ci.cancel();
    }
}
