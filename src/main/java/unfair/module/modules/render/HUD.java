package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
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
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty(
            "Color", 4, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom 1", "Custom 12", "Custom 123"}
    );
    public static final ModeProperty font = new ModeProperty("Font", 0, new String[]{"Unfair", "Minecraft"});
    public final FloatProperty colorSpeed = new FloatProperty("Color Speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("Color Saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("Color Brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("Custom Color 1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("Custom Color 2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("Custom Color 3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
    public final BooleanProperty scoreboard = new BooleanProperty("Scoreboard UI", true);
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

        if (this.isEnabled() && this.scoreboard.getValue() && mc.thePlayer != null && mc.theWorld != null && !mc.gameSettings.showDebugInfo) {
            this.renderScoreboard();
        }

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
    private void renderScoreboard() {
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        ScoreObjective objective = null;
        ScorePlayerTeam playersTeam = scoreboard.getPlayersTeam(mc.thePlayer.getName());
        if (playersTeam != null) {
            int slot = playersTeam.getChatFormat().getColorIndex();
            if (slot >= 0) {
                objective = scoreboard.getObjectiveInDisplaySlot(3 + slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveInDisplaySlot(1);
        }
        if (objective == null) {
            return;
        }

        List<Score> scores = new ArrayList<>();
        for (Score score : scoreboard.getSortedScores(objective)) {
            if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                scores.add(score);
            }
        }
        if (scores.size() > 15) {
            scores = scores.subList(scores.size() - 15, scores.size());
        }
        scores = new ArrayList<>(scores);
        Collections.reverse(scores);

        UFontRenderer titleFont = Unfair.fontManager.getGoogleSansMedium(16);
        UFontRenderer rowFont = Unfair.fontManager.getGoogleSansMedium(14);
        UFontRenderer titleCNFont = Unfair.fontManager.getFont("OPPOSans-Regular", 16);
        UFontRenderer rowCNFont = Unfair.fontManager.getFont("OPPOSans-Regular", 14);
        String title = objective.getDisplayName();
        float pad = 6.0F;
        float rowHeight = Math.max((float) rowFont.getHeight(), (float) rowCNFont.getHeight()) + 1.0F;
        float titleHeight = Math.max((float) titleFont.getHeight(), (float) titleCNFont.getHeight()) + 1.0F;
        float headerGap = 2.0F;

        List<String> names = new ArrayList<>();
        List<String> points = new ArrayList<>();
        float contentWidth = getMixedWidth(title, titleFont, titleCNFont);
        for (Score score : scores) {
            String name = ScorePlayerTeam.formatPlayerName(scoreboard.getPlayersTeam(score.getPlayerName()), score.getPlayerName());
            String value = String.valueOf(score.getScorePoints());
            names.add(name);
            points.add(value);
            contentWidth = Math.max(contentWidth, getMixedWidth(name, rowFont, rowCNFont) + 8.0F + (float) rowFont.getStringWidth(value));
        }

        float width = contentWidth + pad * 2.0F;
        float height = pad * 2.0F + titleHeight + (scores.isEmpty() ? 0.0F : headerGap + rowHeight * scores.size());
        ScaledResolution sr = new ScaledResolution(mc);
        float x = (float) sr.getScaledWidth() - width - 4.0F;
        float y = ((float) sr.getScaledHeight() - height) / 2.0F;
        float radius = 10.5F;

        BlurUtils.prepareBlur();
        RoundedUtils.drawRound(x, y, width, height, radius, Color.BLACK);
        RenderUtil.resetColor();
        BlurUtils.blurEnd(2, 3.0F);

        BlurUtils.prepareBloom();
        RoundedUtils.drawRound(x, y, width, height, radius, Color.BLACK);
        RenderUtil.resetColor();
        BlurUtils.bloomEnd(2, 3.0F);

        RoundedUtils.drawRound(x, y, width, height, radius, new Color(0, 0, 0, 130));

        drawMixedString(title, x + pad, y + pad, Color.WHITE.getRGB(), titleFont, titleCNFont);

        float rowY = y + pad + titleHeight + headerGap;
        int pointsColor = new Color(255, 90, 90).getRGB();
        for (int i = 0; i < scores.size(); i++) {
            String name = names.get(i);
            String value = points.get(i);
            drawMixedString(name, x + pad, rowY, Color.WHITE.getRGB(), rowFont, rowCNFont);
            rowFont.drawString(value, x + width - pad - (float) rowFont.getStringWidth(value), rowY, pointsColor);
            rowY += rowHeight;
        }
    }

    private static boolean isChinese(char c) {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3400' && c <= '\u4DBF')
                || (c >= '\uF900' && c <= '\uFAFF') || (c >= '\u3000' && c <= '\u303F')
                || (c >= '\uFF00' && c <= '\uFFEF');
    }

    private static List<MixedText> splitMixedText(String text) {
        List<MixedText> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String pending = "";
        String activeColor = "";
        boolean started = false;
        boolean currentChinese = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                pending = pending + c + text.charAt(i + 1);
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r') {
                    activeColor = "\u00A7" + text.charAt(i + 1);
                }
                i++;
                continue;
            }
            boolean chinese = isChinese(c);
            if (!started) {
                currentChinese = chinese;
                started = true;
            } else if (chinese != currentChinese) {
                parts.add(new MixedText(buffer.toString(), currentChinese));
                buffer.setLength(0);
                if (pending.isEmpty()) {
                    pending = activeColor;
                }
                currentChinese = chinese;
            }
            buffer.append(pending).append(c);
            pending = "";
        }

        if (buffer.length() > 0) {
            parts.add(new MixedText(buffer.toString(), currentChinese));
        }
        return parts;
    }

    private static float getMixedWidth(String text, UFontRenderer latinFont, UFontRenderer cjkFont) {
        float width = 0.0F;
        for (MixedText part : splitMixedText(text)) {
            width += (float) (part.chinese ? cjkFont : latinFont).getStringWidth(part.text);
        }
        return width;
    }

    private static void drawMixedString(String text, float x, float y, int color, UFontRenderer latinFont, UFontRenderer cjkFont) {
        float drawX = x;
        float cjkY = y + (float) (latinFont.getHeight() - cjkFont.getHeight());
        for (MixedText part : splitMixedText(text)) {
            UFontRenderer font = part.chinese ? cjkFont : latinFont;
            font.drawString(part.text, drawX, part.chinese ? cjkY : y, color);
            drawX += (float) font.getStringWidth(part.text);
        }
    }

    private static class MixedText {
        private final String text;
        private final boolean chinese;

        private MixedText(String text, boolean chinese) {
            this.text = text;
            this.chinese = chinese;
        }
    }
}
