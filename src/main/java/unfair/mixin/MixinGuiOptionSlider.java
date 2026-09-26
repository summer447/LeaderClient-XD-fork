package unfair.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.ui.MaterialYou;
import unfair.util.shader.RoundedUtils;

/**
 * Renders the vanilla {@link GuiOptionSlider} thumb in the vanilla 8px full-height
 * rectangle shape, tinted {@code onPrimary} to stay visible over the M3 filled container.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiOptionSlider.class)
public abstract class MixinGuiOptionSlider {
    @Shadow
    private float sliderValue;

    @Shadow
    public boolean dragging;

    @Shadow
    private GameSettings.Options options;

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        GuiOptionSlider self = (GuiOptionSlider) (Object) this;
        if (!self.visible) {
            ci.cancel();
            return;
        }

        int thumbColor = MaterialYou.getScheme().getOnPrimary();

        if (this.dragging) {
            this.sliderValue = (float) (mouseX - (self.xPosition + 4)) / (float) (self.width - 8);
            this.sliderValue = MathHelper.clamp_float(this.sliderValue, 0.0f, 1.0f);
            float f = this.options.denormalizeValue(this.sliderValue);
            mc.gameSettings.setOptionFloatValue(this.options, f);
            this.sliderValue = this.options.normalizeValue(f);
            self.displayString = mc.gameSettings.getKeyBinding(this.options);
        }

        int thumbSize = self.height - 8;
        int thumbPadX = 4;
        int trackW = self.width - 2 * thumbPadX - thumbSize;
        int thumbX = self.xPosition + thumbPadX + (int) (this.sliderValue * (float) trackW);
        int thumbY = self.yPosition + (self.height - thumbSize) / 2;
        RoundedUtils.drawRound(thumbX, thumbY, (float) thumbSize, (float) thumbSize, thumbSize / 2.0f, thumbColor);

        ci.cancel();
    }
}
