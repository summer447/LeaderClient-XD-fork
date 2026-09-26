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

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从原 HUD 拆分出的独立 ArrayList 模块。
 * 主题色 / 字体类型 / 全局缩放仍取自 HUD（供其他模块统一使用）。
 */
public class ArrayList extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final float SUFFIX_GAP = 3.0F;
    private static final float BAR_WIDTH = 1.0F;
    private static final float TEXT_Y_OFFSET = -0.5F;

    public final IntProperty fontSize = new IntProperty("Font Size", 18, 12, 36);
    public final ModeProperty posX = new ModeProperty("Position X", 0, new String[]{"Left", "Right"});
    public final ModeProperty posY = new ModeProperty("Position Y", 0, new String[]{"Top", "Bottom"});
    public final IntProperty offsetX = new IntProperty("Offset X", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("Offset Y", 2, 0, 255);
    public final IntProperty bgWidth = new IntProperty("Bg Width", 1, 0, 10);
    public final IntProperty bgHeight = new IntProperty("Bg Height", 2, 0, 20);
    public final ColorProperty backgroundColor = new ColorProperty("Background Color", new Color(0, 0, 0, 255).getRGB());
    public final PercentProperty background = new PercentProperty("Background Alpha", 50);

    public final BooleanProperty showBar = new BooleanProperty("Bar", true);
    public final IntProperty barHeight = new IntProperty("Bar Height Less", 0, 0, 10, () -> showBar.getValue());
    public final ModeProperty barColorMode = new ModeProperty("Bar Color", 0, new String[]{"HUD", "Custom"}, () -> showBar.getValue());
    public final ModeProperty barColorStyle = new ModeProperty("Bar Color Style", 0, new String[]{"Rainbow", "Chroma", "Astolfo", "Custom 1", "Custom 12", "Custom 123"}, () -> showBar.getValue() && barColorMode.getValue() == 1);
    public final ColorProperty barCustom1 = new ColorProperty("Bar Custom 1", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 3);
    public final ColorProperty barCustom2 = new ColorProperty("Bar Custom 2", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 4);
    public final ColorProperty barCustom3 = new ColorProperty("Bar Custom 3", Color.WHITE.getRGB(), () -> showBar.getValue() && barColorMode.getValue() == 1 && barColorStyle.getValue() == 5);

    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty interval = new BooleanProperty("Interval", false);
    public final BooleanProperty suffixes = new BooleanProperty("Suffixes", true);
    public final ModeProperty suffixStyle = new ModeProperty("Suffixes Style", 0, new String[]{"Normal", "HUD"}, () -> suffixes.getValue());
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

    /** Bar 为 Custom 模式时的独立配色；HUD 模式返回 null 表示跟随 HUD 主题色 */
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
            List<Module> newActiveModules = Unfair.moduleManager.modules.values().stream()
                    .filter(module -> !module.isHidden() && (module.isEnabled()
                            || this.animationMap.getOrDefault(module, 0.0F) > 0.01F))
                    .collect(Collectors.toList());

            newActiveModules.sort(Comparator.comparingInt(this::getModuleWidth).reversed());

            this.activeModules = newActiveModules;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        float scaleVal = this.hud().scale.getValue();

        if (HUD.font.getValue() == 1) {
            float textHeight = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float padX = (float) this.bgWidth.getValue();
            float padY = (float) this.bgHeight.getValue();
            float rowHeight = textHeight + padY * 2 + 1;
            float barSize = this.showBar.getValue() ? BAR_WIDTH : 0.0F;

            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * scaleVal;
            float y = (float) this.offsetY.getValue() + 1.0F * scaleVal;
            if (this.posX.getValue() == 1) {
                x = (float) new ScaledResolution(mc).getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) new ScaledResolution(mc).getScaledHeight() - y - rowHeight * scaleVal;
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleVal, scaleVal, 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;
            float deltaTime = 1.0F / Math.max(Minecraft.getDebugFPS(), 5);
            float currentY = y;

            ScaledResolution sr = new ScaledResolution(mc);
            float scaledWidth = sr.getScaledWidth() / scaleVal;
            float scaledHeight = sr.getScaledHeight() / scaleVal;
            boolean alignLeft = this.posX.getValue() == 0;
            boolean alignTop = this.posY.getValue() == 0;
            float startX = alignLeft ? this.offsetX.getValue() : scaledWidth - this.offsetX.getValue();
            float startY = alignTop ? this.offsetY.getValue() : scaledHeight - this.offsetY.getValue();

            List<Module> renderList = this.activeModules;

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

                float moduleLeft, moduleRight, textRenderX;
                if (alignLeft) {
                    moduleLeft = currentX;
                    moduleRight = currentX + totalModuleWidth;
                    textRenderX = moduleLeft + barSize + padX;
                } else {
                    moduleRight = currentX;
                    moduleLeft = currentX - totalModuleWidth;
                    textRenderX = moduleLeft + padX;
                }

                float drawY = alignTop ? currentY : currentY - rowHeight;
                int color = this.hud().getColor(l, offset).getRGB();
                int animatedColor = color;
                if (heightFactor < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (heightFactor * 255.0F)));
                    animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
                }

                RenderUtil.enableRenderState();
                if (this.background.getValue() > 0 && heightFactor > 0.02F) {
                    Color bgCol = new Color(this.backgroundColor.getValue());
                    int alpha = (int) (heightFactor * this.background.getValue().floatValue() / 100.0F * 255.0F);
                    alpha = Math.min(255, Math.max(0, alpha));
                    int bgColor = (bgCol.getRGB() & 0x00FFFFFF) | (alpha << 24);
                    RenderUtil.drawRect(moduleLeft, drawY, moduleRight, drawY + rowHeight, bgColor);
                }

                if (this.showBar.getValue() && heightFactor > 0.02F) {
                    int barAlpha = Math.min(255, (int) (heightFactor * 255.0F));
                    int barColor;
                    Color customBarColor = getBarColor(l, offset);
                    if (customBarColor != null) {
                        barColor = (customBarColor.getRGB() & 0x00FFFFFF) | (barAlpha << 24);
                    } else {
                        barColor = (color & 0x00FFFFFF) | (barAlpha << 24);
                    }
                    if (alignLeft) {
                        RenderUtil.drawRect(moduleLeft, drawY + this.barHeight.getValue(), moduleLeft + BAR_WIDTH, drawY + rowHeight - this.barHeight.getValue(), barColor);
                    } else {
                        RenderUtil.drawRect(moduleRight - BAR_WIDTH, drawY + this.barHeight.getValue(), moduleRight, drawY + rowHeight - this.barHeight.getValue(), barColor);
                    }
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (heightFactor > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    float textY = drawY + padY + TEXT_Y_OFFSET;
                    float currentTextX = textRenderX;

                    if (this.shadow.getValue()) {
                        mc.fontRendererObj.drawStringWithShadow(moduleName, currentTextX, textY, animatedColor);
                    } else {
                        mc.fontRendererObj.drawString(moduleName, currentTextX, textY + (alignTop ? 0.0F : 1.0F), animatedColor, false);
                    }
                    currentTextX += textWidth;

                    if (this.suffixes.getValue() && moduleSuffix.length > 0 && heightFactor > 0.5F) {
                        int suffixAlpha = Math.min(255, (int) (((heightFactor - 0.5F) / 0.5F) * 255.0F));
                        int suffixColor;
                        if (suffixStyle.getValue() == 1) {
                            suffixColor = (animatedColor & 0x00FFFFFF) | (suffixAlpha << 24);
                        } else {
                            suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);
                        }
                        for (String suffix : moduleSuffix) {
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
                currentY += alignTop ? effectiveHeight : -effectiveHeight;
                offset++;
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
            ScaledResolution sr = new ScaledResolution(mc);
            float scaledWidth = sr.getScaledWidth() / scaleVal;
            float scaledHeight = sr.getScaledHeight() / scaleVal;

            boolean alignLeft = this.posX.getValue() == 0;
            boolean alignTop = this.posY.getValue() == 0;

            float startX = alignLeft ? this.offsetX.getValue() : scaledWidth - this.offsetX.getValue();
            float startY = alignTop ? this.offsetY.getValue() : scaledHeight - this.offsetY.getValue();

            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleVal, scaleVal, 1.0F);

            long l = System.currentTimeMillis();
            long offset = 0L;
            float deltaTime = 1.0F / Math.max(Minecraft.getDebugFPS(), 5);
            float currentY = startY;

            List<Module> renderList = this.activeModules;

            for (Module module : renderList) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float textWidth = getExactWidth(moduleName, moduleSuffix, fr);
                float fullWidth = textWidth;
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
                float moduleLeft, moduleRight, textRenderX;
                if (alignLeft) {
                    moduleLeft = currentX;
                    moduleRight = currentX + totalModuleWidth;
                    textRenderX = moduleLeft + barSize + padX;
                } else {
                    moduleRight = currentX;
                    moduleLeft = currentX - totalModuleWidth;
                    textRenderX = moduleLeft + padX;
                }
                float drawY = alignTop ? currentY : currentY - rowHeight;
                int color = this.hud().getColor(l, offset).getRGB();
                int animatedColor = color;
                if (heightFactor < 1.0F) {
                    int alpha = Math.max(0, Math.min(255, (int) (heightFactor * 255.0F)));
                    animatedColor = (color & 0x00FFFFFF) | (alpha << 24);
                }

                RenderUtil.enableRenderState();

                if (this.background.getValue() > 0 && heightFactor > 0.02F) {
                    Color bgCol = new Color(this.backgroundColor.getValue());
                    int alpha = (int) (heightFactor * this.background.getValue().floatValue() / 100.0F * 255.0F);
                    alpha = Math.min(255, Math.max(0, alpha));
                    int bgColor = (bgCol.getRGB() & 0x00FFFFFF) | (alpha << 24);
                    RenderUtil.drawRect(moduleLeft, drawY, moduleRight, drawY + rowHeight, bgColor);
                }

                if (this.showBar.getValue() && heightFactor > 0.02F) {
                    int barAlpha = Math.min(255, (int) (heightFactor * 255.0F));
                    int barColor;
                    Color customBarColor = getBarColor(l, offset);
                    if (customBarColor != null) {
                        barColor = (customBarColor.getRGB() & 0x00FFFFFF) | (barAlpha << 24);
                    } else {
                        barColor = (color & 0x00FFFFFF) | (barAlpha << 24);
                    }
                    if (alignLeft) {
                        RenderUtil.drawRect(moduleLeft, drawY + this.barHeight.getValue(), moduleLeft + BAR_WIDTH, drawY + rowHeight - this.barHeight.getValue(), barColor);
                    } else {
                        RenderUtil.drawRect(moduleRight - BAR_WIDTH, drawY + this.barHeight.getValue(), moduleRight, drawY + rowHeight - this.barHeight.getValue(), barColor);
                    }
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();
                if (heightFactor > 0.05F) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    float textY = drawY + (padY / 2.0F) + TEXT_Y_OFFSET;
                    float currentTextX = textRenderX;

                    fr.drawString(moduleName, currentTextX, textY, animatedColor, this.shadow.getValue());
                    currentTextX += fr.getStringWidth(moduleName);

                    if (this.suffixes.getValue() && moduleSuffix.length > 0 && heightFactor > 0.5F) {
                        int suffixAlpha = Math.min(255, (int) (((heightFactor - 0.5F) / 0.5F) * 255.0F));
                        int suffixColor;
                        if (suffixStyle.getValue() == 1) {
                            suffixColor = (animatedColor & 0x00FFFFFF) | (suffixAlpha << 24);
                        } else {
                            suffixColor = (ChatColors.GRAY.toAwtColor() & 0x00FFFFFF) | (suffixAlpha << 24);
                        }
                        for (String string : moduleSuffix) {
                            currentTextX += SUFFIX_GAP;
                            fr.drawString(string, currentTextX, textY, suffixColor, this.shadow.getValue());
                            currentTextX += fr.getStringWidth(string);
                        }
                    }
                    GlStateManager.disableBlend();
                }
                currentY += alignTop ? effectiveHeight : -effectiveHeight;
                offset++;
            }

            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}
