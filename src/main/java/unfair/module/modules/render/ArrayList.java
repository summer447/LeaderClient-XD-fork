package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.enums.ChatColors;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.Render2DEvent;
import unfair.events.TickEvent;
import unfair.font.impl.UFontRenderer;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.ColorUtil;
import unfair.util.RenderUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayList extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final float SUFFIX_GAP = 3.0F;
    private static final float BAR_WIDTH = 1.0F;
    private static final float TEXT_Y_OFFSET = -0.5F;
    private static final boolean alignTop = true;
    public final IntProperty fontSize = new IntProperty("Font Size", 18, 12, 36);
    public final ModeProperty posX = new ModeProperty("Position X", 0, new String[]{"Left", "Right"});
    public final IntProperty offsetX = new IntProperty("Offset X", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("Offset Y", 20, 20, 255);
    public final IntProperty bgWidth = new IntProperty("Bg Width", 1, 0, 10);
    public final IntProperty bgHeight = new IntProperty("Bg Height", 2, 0, 20);
    public final ColorProperty backgroundColor = new ColorProperty("Background Color", new Color(0, 0, 0, 255).getRGB());
    public final PercentProperty background = new PercentProperty("Background Alpha", 0, 0, 30, null);
    public final BooleanProperty blur = new BooleanProperty("blur", true);
    public final IntProperty radius = new IntProperty("Radius", 3, 0, 10);

    public final BooleanProperty showBar = new BooleanProperty("Bar", true);
    public final IntProperty barHeight = new IntProperty("Bar Height Less", 0, 0, 10, showBar::getValue);
    public final ModeProperty barColorMode = new ModeProperty("Bar Color", 0, new String[]{"HUD", "Custom"}, showBar::getValue);
    public final ModeProperty barColorStyle = new ModeProperty("Bar Color Style", 0, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom 1", "Custom 12", "Custom 123"}, () -> showBar.getValue() && barColorMode.getValue() == 1);
    public final ColorProperty barCustom1 = new ColorProperty("Bar Custom 1", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 3);
    public final ColorProperty barCustom2 = new ColorProperty("Bar Custom 2", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 4);
    public final ColorProperty barCustom3 = new ColorProperty("Bar Custom 3", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 5);

    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty interval = new BooleanProperty("Interval", false);
    public final BooleanProperty suffixes = new BooleanProperty("Suffixes", true);
    public final ModeProperty suffixStyle = new ModeProperty("Suffixes Style", 0, new String[]{"Normal", "HUD"}, suffixes::getValue);
    public final BooleanProperty lowerCase = new BooleanProperty("Lower Case", false);

    private final Map<Module, Float> animationMap = new HashMap<>();
    private List<Module> activeModules = new java.util.ArrayList<>();

    private static float animateSmooth(float target, float current, float speed, float deltaTime) {
        float diff = target - current;
        float change = diff * Math.min(1.0f, speed * deltaTime);
        if (Math.abs(change) < 0.01f && Math.abs(diff) < 0.01f) return target;
        return current + change;
    }

    public ArrayList() {
        super("ArrayList", true, true);
    }

    @Override
    public void onDisabled() {
        this.animationMap.clear();
        this.activeModules = new java.util.ArrayList<>();
    }

    private HUD hud() {
        return (HUD) Unfair.moduleManager.getModule(HUD.class);
    }

    private UFontRenderer getCurrentFontRenderer() {
        int size = this.fontSize.getValue();
        if (HUD.font.getValue() == 0) {
            return Unfair.fontManager.getFont(size);
        }
        return null;
    }

    private String addSpacesBeforeCapitals(String str) {
        if (str == null || str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        if (this.interval.getValue()) {
            moduleName = addSpacesBeforeCapitals(moduleName);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        if (HUD.font.getValue() == 1) {
            int width = mc.fontRendererObj.getStringWidth(string);
            if (this.suffixes.getValue()) {
                for (String str : arr) {
                    width += 3 + mc.fontRendererObj.getStringWidth(str);
                }
            }
            return width;
        } else {
            UFontRenderer fr = getCurrentFontRenderer();
            if (fr == null) return 0;
            int width = fr.getStringWidth(string);
            if (this.suffixes.getValue()) {
                for (String str : arr) {
                    width += 3 + fr.getStringWidth(str);
                }
            }
            return width;
        }
    }

    private float getExactWidth(String string, String[] arr, UFontRenderer fr) {
        float width = fr.getStringWidth(string);
        if (this.suffixes.getValue() && arr != null) {
            for (String str : arr) {
                width += SUFFIX_GAP + fr.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long time, long offset) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.hud().colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(time - offset * 300L) % speed) / (float) speed;
    }

    private Color getBarColor(long time, long offset) {
        if (barColorMode.getValue() == 0) {
            return null;
        } else {
            Color color = Color.white;
            switch (barColorStyle.getValue()) {
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
                    color = new Color(this.barCustom1.getValue());
                    break;
                case 4:
                    double cycle1 = this.getColorCycle(time, offset);
                    color = ColorUtil.interpolate(
                            (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                            new Color(this.barCustom1.getValue()),
                            new Color(this.barCustom2.getValue())
                    );
                    break;
                case 5:
                    double cycle2 = this.getColorCycle(time, offset);
                    float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                    if (floor <= 0.5F) {
                        color = ColorUtil.interpolate(floor * 2.0F, new Color(this.barCustom1.getValue()), new Color(this.barCustom2.getValue()));
                    } else {
                        color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.barCustom2.getValue()), new Color(this.barCustom3.getValue()));
                    }
                    break;
            }
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            HUD hud = this.hud();
            return Color.getHSBColor(
                    hsb[0],
                    hsb[1] * (hud.colorSaturation.getValue().floatValue() / 100.0F),
                    hsb[2] * (hud.colorBrightness.getValue().floatValue() / 100.0F)
            );
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {

            this.activeModules = Unfair.moduleManager.modules.values().stream()
                    .filter(module -> !module.isHidden() && (module.isEnabled()
                            || this.animationMap.getOrDefault(module, 0.0F) > 0.01F)).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.toList());
        }
    }

    private static class RowData {
        String name;
        String[] suffix;
        float moduleLeft, moduleRight, drawY, rowHeight, textRenderX, textWidth, heightFactor;
        float rPrev, rNext;
        int color, animatedColor;
        long offset;
    }

    private void applyCornerRadii(List<RowData> rows) {
        float r = this.radius.getValue();
        for (RowData rd : rows) {
            rd.rPrev = r;
            rd.rNext = r;
        }
    }

    private void drawMergedRects(List<RowData> rows, float scaleVal, boolean alignTop, boolean blurMask) {
        int i = 0;
        while (i < rows.size()) {
            RowData groupFirst = rows.get(i);
            float groupWidth = groupFirst.moduleRight - groupFirst.moduleLeft;
            int j = i;
            while (j + 1 < rows.size() && rows.get(j).rNext == 0.0F && rows.get(j + 1).rPrev == 0.0F
                    && Math.abs((rows.get(j + 1).moduleRight - rows.get(j + 1).moduleLeft) - groupWidth) <= 2.0F) j++;
            RowData first = rows.get(i);
            RowData last = rows.get(j);
            float left = first.moduleLeft * scaleVal;
            float width = (first.moduleRight - first.moduleLeft) * scaleVal;
            float top, height, rTop, rBottom;
            if (alignTop) {
                top = first.drawY;
                height = last.drawY + last.rowHeight - first.drawY;
                rTop = first.rPrev;
                rBottom = last.rNext;
            } else {
                top = last.drawY;
                height = first.drawY + first.rowHeight - last.drawY;
                rTop = last.rNext;
                rBottom = first.rPrev;
            }
            top *= scaleVal;
            height *= scaleVal;

            boolean hasAbove = alignTop ? i > 0 : i < rows.size() - 1;
            boolean hasBelow = alignTop ? i < rows.size() - 1 : i > 0;

            Color color;
            float ext = this.radius.getValue() * scaleVal;
            if (hasAbove) {
                top -= ext;
                height += ext;
            }
            if (hasBelow) {
                height += ext;
            }
            if (blurMask) {
                color = Color.BLACK;
            } else {
                int alpha = (int) (first.heightFactor * this.background.getValue().floatValue() / 100.0F * 255.0F);
                alpha = Math.min(255, Math.max(0, alpha));
                if (alpha <= 0) {
                    i = j + 1;
                    continue;
                }
                color = new Color((new Color(this.backgroundColor.getValue()).getRGB() & 0x00FFFFFF) | (alpha << 24), true);
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.0F, 0.0F, -0.01F * (i + 1));
                RoundedUtils.INSTANCE.drawRound(left, top, width, height, rTop, rTop, rBottom, rBottom, color);
                GlStateManager.popMatrix();
                i = j + 1;
                continue;
            }
            RoundedUtils.INSTANCE.drawRound(left, top, width, height, rTop, rTop, rBottom, rBottom, color);
            i = j + 1;
        }
    }
    private void renderBlur(List<RowData> rows, float scaleVal, boolean alignTop) {
        applyCornerRadii(rows);
        if (!this.blur.getValue() || rows.isEmpty()) return;
        BlurUtils.prepareBlur();
        drawMergedRects(rows, scaleVal, alignTop, true);
        RenderUtil.resetColor();
        BlurUtils.blurEnd(2, 3.0F);
    }

    private void drawBackgrounds(List<RowData> rows, float scaleVal, boolean alignTop) {
        if (this.background.getValue() > 0) {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GL11.glDepthFunc(GL11.GL_LESS);
            drawMergedRects(rows, scaleVal, alignTop, false);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableDepth();
        }
    }

    private void drawBar(RowData rd, long time, boolean alignLeft) {
        if (this.showBar.getValue() && rd.heightFactor > 0.02F) {
            int barAlpha = Math.min(255, (int) (rd.heightFactor * 255.0F));
            int barColor;
            Color customBarColor = getBarColor(time, rd.offset);
            if (customBarColor != null) {
                barColor = (customBarColor.getRGB() & 0x00FFFFFF) | (barAlpha << 24);
            } else {
                barColor = (rd.color & 0x00FFFFFF) | (barAlpha << 24);
            }
            if (alignLeft) {
                RenderUtil.drawRect(rd.moduleLeft, rd.drawY + this.barHeight.getValue(), rd.moduleLeft + BAR_WIDTH, rd.drawY + rd.rowHeight - this.barHeight.getValue(), barColor);
            } else {
                RenderUtil.drawRect(rd.moduleRight - BAR_WIDTH, rd.drawY + this.barHeight.getValue(), rd.moduleRight, rd.drawY + rd.rowHeight - this.barHeight.getValue(), barColor);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        float scaleVal = this.hud().scale.getValue();

        ScaledResolution sr = new ScaledResolution(mc);
        float scaledWidth = sr.getScaledWidth() / scaleVal;
        float scaledHeight = sr.getScaledHeight() / scaleVal;
        boolean alignLeft = this.posX.getValue() == 0;
        float startX = alignLeft ? this.offsetX.getValue() : scaledWidth - this.offsetX.getValue();

        long l = System.currentTimeMillis();
        float deltaTime = 1.0F / Math.max(Minecraft.getDebugFPS(), 5);
        List<Module> renderList = this.activeModules;

        if (HUD.font.getValue() == 1) {
            float textHeight = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float padX = (float) this.bgWidth.getValue();
            float padY = (float) this.bgHeight.getValue();
            float rowHeight = textHeight + padY * 2 + 1;
            float barSize = this.showBar.getValue() ? BAR_WIDTH : 0.0F;

            float y = (float) this.offsetY.getValue() + scaleVal;

            long offset = 0L;
            float currentY = y;
            List<RowData> rows = new java.util.ArrayList<>();

            for (Module module : renderList) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float textWidth = mc.fontRendererObj.getStringWidth(moduleName);
                float totalWidth = textWidth;
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    for (String s : moduleSuffix) {
                        totalWidth += SUFFIX_GAP + mc.fontRendererObj.getStringWidth(s);
                    }
                }

                float targetSlide = module.isEnabled() ? totalWidth : 0.0F;
                float currentSlide = this.animationMap.getOrDefault(module, 0.0F);
                currentSlide = animateSmooth(targetSlide, currentSlide, 10.0F, deltaTime);
                currentSlide = Math.max(0.0F, Math.min(totalWidth, currentSlide));
                this.animationMap.put(module, currentSlide);
                float heightFactor = (totalWidth > 0.0F) ? (currentSlide / totalWidth) : 0.0F;
                float effectiveHeight = rowHeight * heightFactor;
                if (currentSlide <= 0.1F) {
                    offset++;
                    continue;
                }

                float totalModuleWidth = barSize + padX * 2 + totalWidth;
                float xSlideAmount = (1.0F - heightFactor) * (totalModuleWidth + 5.0F);
                float currentX = startX + (alignLeft ? -xSlideAmount : xSlideAmount);

                RowData rd = new RowData();
                rd.name = moduleName;
                rd.suffix = moduleSuffix;
                rd.textWidth = textWidth;
                rd.heightFactor = heightFactor;
                rd.rowHeight = rowHeight;
                rd.offset = offset;
                if (alignLeft) {
                    rd.moduleLeft = currentX;
                    rd.moduleRight = currentX + totalModuleWidth;
                    rd.textRenderX = rd.moduleLeft + barSize + padX;
                } else {
                    rd.moduleRight = currentX;
                    rd.moduleLeft = currentX - totalModuleWidth;
                    rd.textRenderX = rd.moduleLeft + padX;
                }
                rd.drawY = alignTop ? currentY : currentY - rowHeight;

                rd.color = this.hud().getColor(l, offset).getRGB();
                rd.animatedColor = rd.color;
                if (heightFactor < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (heightFactor * 255.0F)));
                    rd.animatedColor = (rd.color & 0x00FFFFFF) | (alpha << 24);
                }
                rows.add(rd);

                currentY += alignTop ? effectiveHeight : -effectiveHeight;
                offset++;
            }

            renderBlur(rows, scaleVal, alignTop);
            drawBackgrounds(rows, scaleVal, alignTop);

            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleVal, scaleVal, 0.0F);
            for (RowData rd : rows) {
                RenderUtil.enableRenderState();
                drawBar(rd, l, alignLeft);
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (rd.heightFactor > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    float textY = rd.drawY + padY + TEXT_Y_OFFSET;
                    float currentTextX = rd.textRenderX;

                    if (this.shadow.getValue()) {
                        mc.fontRendererObj.drawStringWithShadow(rd.name, currentTextX, textY, rd.animatedColor);
                    } else {
                        mc.fontRendererObj.drawString(rd.name, currentTextX, textY + (alignTop ? 0.0F : 1.0F), rd.animatedColor, false);
                    }
                    currentTextX += rd.textWidth;

                    if (this.suffixes.getValue() && rd.suffix.length > 0 && rd.heightFactor > 0.5F) {
                        int suffixAlpha = Math.min(255, (int) (((rd.heightFactor - 0.5F) / 0.5F) * 255.0F));
                        int suffixColor;
                        if (suffixStyle.getValue() == 1) {
                            suffixColor = (rd.animatedColor & 0x00FFFFFF) | (suffixAlpha << 24);
                        } else {
                            suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);
                        }
                        for (String suffix : rd.suffix) {
                            currentTextX += SUFFIX_GAP;
                            if (this.shadow.getValue()) {
                                mc.fontRendererObj.drawStringWithShadow(suffix, currentTextX, textY, suffixColor);
                            } else {
                                mc.fontRendererObj.drawString(suffix, currentTextX, textY + (alignTop ? 0.0F : 1.0F), suffixColor, false);
                            }
                            currentTextX += mc.fontRendererObj.getStringWidth(suffix);
                        }
                    }
                    GlStateManager.disableBlend();
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        } else {
            UFontRenderer fr = getCurrentFontRenderer();
            if (fr == null) return;

            float fontHeight = (float) fr.getHeight();
            float padX = (float) this.bgWidth.getValue();
            float padY = (float) this.bgHeight.getValue();
            float rowHeight = fontHeight + padY + 1;
            float startY = alignTop ? this.offsetY.getValue() : scaledHeight - this.offsetY.getValue();

            long offset = 0L;
            float currentY = startY;
            List<RowData> rows = new java.util.ArrayList<>();

            for (Module module : renderList) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float fullWidth = getExactWidth(moduleName, moduleSuffix, fr);
                float targetSlide = module.isEnabled() ? fullWidth : 0.0F;
                float currentSlide = this.animationMap.getOrDefault(module, 0.0F);
                currentSlide = animateSmooth(targetSlide, currentSlide, 15.0F, deltaTime);
                currentSlide = Math.max(0.0F, Math.min(fullWidth, currentSlide));
                this.animationMap.put(module, currentSlide);
                float heightFactor = (fullWidth > 0.0F) ? (currentSlide / fullWidth) : 0.0F;
                float effectiveHeight = rowHeight * heightFactor;
                if (currentSlide <= 0.1F) {
                    offset++;
                    continue;
                }
                float barSize = this.showBar.getValue() ? BAR_WIDTH : 0.0F;
                float totalModuleWidth = barSize + padX * 2 + fullWidth;
                float xSlideAmount = (1.0F - heightFactor) * (totalModuleWidth + 5.0F);
                float currentX = startX + (alignLeft ? -xSlideAmount : xSlideAmount);

                RowData rd = new RowData();
                rd.name = moduleName;
                rd.suffix = moduleSuffix;
                rd.textWidth = fr.getStringWidth(moduleName);
                rd.heightFactor = heightFactor;
                rd.rowHeight = rowHeight;
                rd.offset = offset;
                if (alignLeft) {
                    rd.moduleLeft = currentX;
                    rd.moduleRight = currentX + totalModuleWidth;
                    rd.textRenderX = rd.moduleLeft + barSize + padX;
                } else {
                    rd.moduleRight = currentX;
                    rd.moduleLeft = currentX - totalModuleWidth;
                    rd.textRenderX = rd.moduleLeft + padX;
                }
                rd.drawY = alignTop ? currentY : currentY - rowHeight;

                rd.color = this.hud().getColor(l, offset).getRGB();
                rd.animatedColor = rd.color;
                if (heightFactor < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (heightFactor * 255.0F)));
                    rd.animatedColor = (rd.color & 0x00FFFFFF) | (alpha << 24);
                }
                rows.add(rd);

                currentY += alignTop ? effectiveHeight : -effectiveHeight;
                offset++;
            }

            renderBlur(rows, scaleVal, alignTop);
            drawBackgrounds(rows, scaleVal, alignTop);

            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleVal, scaleVal, 1.0F);
            for (RowData rd : rows) {
                RenderUtil.enableRenderState();
                drawBar(rd, l, alignLeft);
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (rd.heightFactor > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    float textY = rd.drawY + (padY / 2.0F) + TEXT_Y_OFFSET;
                    float currentTextX = rd.textRenderX;

                    fr.drawString(rd.name, currentTextX, textY, rd.animatedColor, this.shadow.getValue());
                    currentTextX += rd.textWidth;

                    if (this.suffixes.getValue() && rd.suffix.length > 0 && rd.heightFactor > 0.5F) {
                        int suffixAlpha = Math.min(255, (int) (((rd.heightFactor - 0.5F) / 0.5F) * 255.0F));
                        int suffixColor;
                        if (suffixStyle.getValue() == 1) {
                            suffixColor = (rd.animatedColor & 0x00FFFFFF) | (suffixAlpha << 24);
                        } else {
                            suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);
                        }
                        for (String string : rd.suffix) {
                            currentTextX += SUFFIX_GAP;
                            fr.drawString(string, currentTextX, textY, suffixColor, this.shadow.getValue());
                            currentTextX += fr.getStringWidth(string);
                        }
                    }
                    GlStateManager.disableBlend();
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}
