package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.enums.BlinkModules;
import unfair.event.EventTarget;
import unfair.events.Render2DEvent;
import unfair.font.impl.UFontRenderer;
import unfair.mixin.IAccessorGuiChat;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.ColorUtil;
import unfair.util.RenderUtil;

import java.awt.*;

/**
 * HUD：全局主题色 / 字体 / 缩放，以及聊天描边、Blink 计时、开关音效与提示。
 * 模块 ArrayList 已拆分为独立模块 {@link ArrayList}。
 */
public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty colorMode = new ModeProperty(
            "Color", 3, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom 1", "Custom 12", "Custom 123"}
    );
    public static final ModeProperty font = new ModeProperty("Font", 0, new String[]{"Unfair", "Minecraft"});
    public final FloatProperty colorSpeed = new FloatProperty("Color Speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("Color Saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("Color Brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("Custom Color 1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("Custom Color 2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("Custom Color 3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);

    public final BooleanProperty chatOutline = new BooleanProperty("Chat Outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("Blink Timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("Toggle Sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("Toggle Alerts", false);

    public HUD() {
        super("HUD", true, true);
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        // 命令输入框描边
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Unfair.commandManager != null && Unfair.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }

        // Blink 滞留移动包计数（屏幕中央）
        if (this.isEnabled() && this.blinkTimer.getValue() && !mc.gameSettings.showDebugInfo) {
            BlinkModules blinkingModule = Unfair.blinkManager.getBlinkingModule();
            if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                long movementPacketSize = Unfair.blinkManager.countMovement();
                if (movementPacketSize > 0L) {
                    float scaleVal = this.scale.getValue();
                    ScaledResolution sr = new ScaledResolution(mc);
                    String text = String.valueOf(movementPacketSize);
                    int color = this.getColor(System.currentTimeMillis()).getRGB() & 0x00FFFFFF | 0xBF000000;

                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scaleVal, scaleVal, 1.0F);
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.disableDepth();

                    float scaledWidth = sr.getScaledWidth() / scaleVal;
                    float scaledHeight = sr.getScaledHeight() / scaleVal;
                    if (font.getValue() == 0) {
                        UFontRenderer fr = Unfair.fontManager.getFont(18);
                        fr.drawString(text, scaledWidth / 2.0F - (float) fr.getStringWidth(text) / 2.0F,
                                scaledHeight * 0.6F, color, true);
                    } else {
                        mc.fontRendererObj.drawString(text,
                                scaledWidth / 2.0F - (float) mc.fontRendererObj.getStringWidth(text) / 2.0F,
                                scaledHeight * 0.6F, color, true);
                    }

                    GlStateManager.enableDepth();
                    GlStateManager.disableBlend();
                    GlStateManager.popMatrix();
                }
            }
        }
    }
}
