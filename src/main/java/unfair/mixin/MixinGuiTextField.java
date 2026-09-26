package unfair.mixin;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
 * Material You filled text field:
 * - Container: surfaceContainerHighest with only the top corners rounded (4px)
 * - Indicator: 1px outlineVariant, 2px primary when focused
 * - Text: onSurface; selection/caret: primary
 * Custom text colors set via setTextColor are still honored.
 */
@SideOnly(Side.CLIENT)
@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {
    @Shadow
    private FontRenderer fontRendererInstance;
    @Shadow
    private String text;
    @Shadow
    private int cursorCounter;
    @Shadow
    private boolean enableBackgroundDrawing;
    @Shadow
    private boolean isFocused;
    @Shadow
    private boolean isEnabled;
    @Shadow
    private int lineScrollOffset;
    @Shadow
    private int cursorPosition;
    @Shadow
    private int selectionEnd;
    @Shadow
    private int enabledColor;
    @Shadow
    private int disabledColor;

    @Inject(method = "drawTextBox", at = @At("HEAD"), cancellable = true)
    private void onDrawTextBox(CallbackInfo ci) {
        ci.cancel();
        GuiTextField self = (GuiTextField) (Object) this;
        if (!self.getVisible()) {
            return;
        }

        MaterialYou.Scheme scheme = MaterialYou.getScheme();

        if (this.enableBackgroundDrawing) {
            int container = this.isEnabled
                    ? scheme.getSurfaceContainerHighest()
                    : MaterialYou.withAlphaFraction(scheme.getOnSurface(), 0.04f);
            RoundedUtils.drawRoundedRectRise(
                    self.xPosition - 1, self.yPosition - 1,
                    self.width + 2, self.height + 2,
                    4.0f, container,
                    true, true, false, false);
            int indicatorColor;
            if (!this.isEnabled) {
                indicatorColor = MaterialYou.withAlphaFraction(scheme.getOnSurface(), MaterialYou.DISABLED_CONTENT_ALPHA);
            } else if (this.isFocused) {
                indicatorColor = scheme.getPrimary();
            } else {
                indicatorColor = scheme.getOutlineVariant();
            }
            int indicatorHeight = this.isFocused ? 2 : 1;
            Gui.drawRect(self.xPosition, self.yPosition + self.height - indicatorHeight,
                    self.xPosition + self.width, self.yPosition + self.height, indicatorColor);
        }

        int textColor;
        if (this.isEnabled) {
            textColor = this.enabledColor != 0xE0E0E0 ? this.enabledColor : scheme.getOnSurface();
        } else {
            textColor = this.disabledColor != 0x707070
                    ? this.disabledColor
                    : MaterialYou.withAlphaFraction(scheme.getOnSurface(), MaterialYou.DISABLED_CONTENT_ALPHA);
        }

        int cursorRel = this.cursorPosition - this.lineScrollOffset;
        int selectionRel = this.selectionEnd - this.lineScrollOffset;
        String visibleText = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), self.getWidth());
        boolean cursorInView = cursorRel >= 0 && cursorRel <= visibleText.length();
        boolean showCursor = this.isFocused && this.cursorCounter / 6 % 2 == 0 && cursorInView;
        int textX = this.enableBackgroundDrawing ? self.xPosition + 4 : self.xPosition;
        int textY = this.enableBackgroundDrawing ? self.yPosition + (self.height - 8) / 2 : self.yPosition;
        int afterCursorX = textX;

        if (selectionRel > visibleText.length()) {
            selectionRel = visibleText.length();
        }
        if (visibleText.length() > 0) {
            String beforeCursor = cursorInView ? visibleText.substring(0, cursorRel) : visibleText;
            afterCursorX = this.fontRendererInstance.drawStringWithShadow(beforeCursor, (float) textX, (float) textY, textColor);
        }

        boolean cursorAtEnd = this.cursorPosition < this.text.length() || this.text.length() >= self.getMaxStringLength();
        int cursorX = afterCursorX;
        if (!cursorInView) {
            cursorX = cursorRel > 0 ? textX + self.width : textX;
        } else if (cursorAtEnd) {
            cursorX = afterCursorX - 1;
            --afterCursorX;
        }

        if (visibleText.length() > 0 && cursorInView && cursorRel < visibleText.length()) {
            this.fontRendererInstance.drawStringWithShadow(visibleText.substring(cursorRel), (float) afterCursorX, (float) textY, textColor);
        }

        if (showCursor) {
            if (cursorAtEnd) {
                Gui.drawRect(cursorX, textY - 1, cursorX + 1, textY + 1 + this.fontRendererInstance.FONT_HEIGHT, scheme.getPrimary());
            } else {
                this.fontRendererInstance.drawStringWithShadow("_", (float) cursorX, (float) textY, textColor);
            }
        }

        if (selectionRel != cursorRel) {
            int selectionX = textX + this.fontRendererInstance.getStringWidth(visibleText.substring(0, selectionRel));
            this.leader$drawSelectionHighlight(self, cursorX, textY - 1, selectionX - 1, textY + 1 + this.fontRendererInstance.FONT_HEIGHT, scheme.getPrimary());
        }
    }

    /**
     * Vanilla selection highlight (OR_REVERSE color logic), tinted with the scheme primary color.
     */
    private void leader$drawSelectionHighlight(GuiTextField self, int startX, int startY, int endX, int endY, int rgb) {
        int tmp;
        if (startX < endX) {
            tmp = startX;
            startX = endX;
            endX = tmp;
        }
        if (startY < endY) {
            tmp = startY;
            startY = endY;
            endY = tmp;
        }
        if (endX > self.xPosition + self.width) {
            endX = self.xPosition + self.width;
        }
        if (startX > self.xPosition + self.width) {
            startX = self.xPosition + self.width;
        }
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.color(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f, 1.0f);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(5387);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos(startX, endY, 0.0).endVertex();
        worldrenderer.pos(endX, endY, 0.0).endVertex();
        worldrenderer.pos(endX, startY, 0.0).endVertex();
        worldrenderer.pos(startX, startY, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }
}
