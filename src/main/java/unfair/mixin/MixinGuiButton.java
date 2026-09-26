package unfair.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.ui.MaterialButtonRenderer;

/**
 * Renders vanilla {@link GuiButton}s as Material You filled buttons.
 * Subclasses that override {@code drawButton} and call {@code super.drawButton}
 * (e.g. {@code GuiButtonLanguage}) also get the M3 container underneath;
 * their icon is drawn on top by their own code.
 * The slider thumb is drawn by {@link MixinGuiOptionSlider} via {@code mouseDragged}.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiButton.class)
public abstract class MixinGuiButton {
    @Shadow
    protected boolean hovered;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void onDrawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        GuiButton self = (GuiButton) (Object) this;
        ci.cancel();
        this.hovered = MaterialButtonRenderer.drawContainer(self, mc, mouseX, mouseY);
        this.mouseDragged(mc, mouseX, mouseY);
        MaterialButtonRenderer.drawLabel(self, mc);
    }
}
