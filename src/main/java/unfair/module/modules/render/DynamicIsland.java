package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PacketEvent;
import unfair.events.Render2DEvent;
import unfair.events.TickEvent;
import unfair.font.impl.UFontRenderer;
import unfair.module.Module;
import unfair.module.modules.player.ChestStealer;
import unfair.property.properties.BooleanProperty;
import unfair.util.RenderUtil;
import unfair.util.TimerUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicIsland extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String CLIENT_NAME = Unfair.Companion.getCLIENT_NAME();
    public static final int LOW = 0;
    public static final int MEDIUM = 1;
    public static final int TOP = 2;
    public static final int SUPREME = 3;
    private final BooleanProperty moduleToggle = new BooleanProperty("Module Toggle", true);
    private final BooleanProperty containerPreview = new BooleanProperty("Container Preview", true);
    private final BooleanProperty tab = new BooleanProperty("Tab Preview", true);
    private final BooleanProperty playerAvatar = new BooleanProperty("Player Avatar", true, tab::getValue);
    private final BooleanProperty blur = new BooleanProperty("Blur", true);
    private final BooleanProperty bloom = new BooleanProperty("Bloom", true);

    private final List<TimedContent> timedContents = new ArrayList<>();
    private final List<Content> contents = new ArrayList<>();
    private final Map<Module, Boolean> lastStates = new HashMap<>();

    private float renderX, width, height;
    private float currentContentY;
    private float[] containerPrevSlotColor;
    private float[] containerSlotColor;
    private long lastFrame = System.currentTimeMillis();
    private long frameDelta = 16L;

    public DynamicIsland() {
        super("DynamicIsland", false);
    }

    @Override
    public void onDisabled() {
        this.timedContents.clear();
        this.contents.clear();
        this.lastStates.clear();
        this.containerSlotColor = null;
        this.containerPrevSlotColor = null;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || !this.moduleToggle.getValue()) return;
        for (Module module : Unfair.moduleManager.modules.values()) {
            if (module == this || module.isHidden()) continue;
            boolean now = module.isEnabled();
            Boolean last = this.lastStates.put(module, now);
            if (last != null && last != now) {
                this.addContent(module);
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;

        long now = System.currentTimeMillis();
        this.frameDelta = now - this.lastFrame;
        this.lastFrame = now;

        final UFontRenderer titleFont = Unfair.fontManager.getGoogleSansMedium(16);
        final UFontRenderer descFont = Unfair.fontManager.getGoogleSansMedium(14);
        final ScaledResolution sr = new ScaledResolution(mc);

        if (!this.timedContents.isEmpty()) {
            for (final TimedContent content : new ArrayList<>(this.timedContents)) {
                if (!content.timer.hasTimeElapsed(content.time)) {
                    this.contents.add(new Content(() -> {
                        switch (content.type) {
                            case MODULE: {
                                if (content.module != null) {
                                    content.animation.setDirection(content.module.isEnabled());
                                    double animation = content.animation.getOutput();
                                    RoundedUtils.drawRound(6, 13, 26, 14, 6.5f, Color.DARK_GRAY);
                                    RoundedUtils.drawRound(7, 14, 24, 12, 6, new Color((int) (128 - 80 * animation), (int) (128 - 11 * animation), (int) (128 + 19 * animation), 255));
                                    RoundedUtils.drawRound((float) (8 + 12 * animation), 15, 10, 10, 4.5f, Color.DARK_GRAY);
                                }
                                break;
                            }
                            case SUCCESS: {
                                if (content.timer.hasTimeElapsed(content.time - 200)) {
                                    content.animation.setDirection(false);
                                }
                                double animation = content.animation.getOutput();
                                RoundedUtils.drawRound(7, 8, 24, 24, 7, new Color(95, 143, 80, 200));
                                RenderUtil.scissor(this.renderX + 7, this.currentContentY + 8, 24 * animation, 24);
                                this.drawCheckIcon();
                                RenderUtil.scissor(this.renderX, 15, this.width, this.height);
                                break;
                            }
                            case WARNING: {
                                if (content.timer.hasTimeElapsed(content.time - 200)) {
                                    content.animation.setDirection(false);
                                }
                                double animation = content.animation.getOutput();
                                RoundedUtils.drawRound(7, 8, 24, 24, 7, new Color(143, 80, 80, 200));
                                RenderUtil.scissor(this.renderX + 7, this.currentContentY + 8, 24, 24 * animation);
                                this.drawWarnIcon();
                                RenderUtil.scissor(this.renderX, 15, this.width, this.height);
                                break;
                            }
                            case INFO: {
                                if (content.timer.hasTimeElapsed(content.time - 200)) {
                                    content.animation.setDirection(false);
                                }
                                double animation = content.animation.getOutput();
                                RoundedUtils.drawRound(7, 8, 24, 24, 7, new Color(48, 117, 147, 200));
                                RoundedUtils.drawRound(18.2f, 11, 1.5f, (float) (18 * animation), 0.75f, Color.WHITE);
                                RoundedUtils.drawRound(10, 19, (float) (18 * animation), 1.5f, 0.75f, Color.WHITE);
                                break;
                            }
                            default:
                                RoundedUtils.drawRound(7, 8, 24, 24, 7, new Color(200, 200, 200, 200));
                        }

                        titleFont.drawString(content.title, 38, 9.5f, -1);

                        if (content.type == ContentType.MODULE && content.module != null) {
                            content.description = content.module.getName() + (content.module.isEnabled() ? " has been §2Enabled" : " has been §4Disabled");
                        }
                        descFont.drawString(content.description, 38, 24.5f, -1);
                    }, 50 + Math.max(titleFont.getStringWidth(content.title), descFont.getStringWidth(content.description)), 40, LOW));
                } else {
                    this.timedContents.remove(content);
                }
            }
        }

        final boolean showContainerPreview = this.containerPreview.getValue() && this.isChestStealerEnabled();

        if (showContainerPreview && mc.thePlayer.openContainer instanceof ContainerChest) {
            final ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
            this.contents.clear();

            if (containerPrevSlotColor == null || containerPrevSlotColor.length != container.inventorySlots.size() - 36) {
                containerPrevSlotColor = new float[container.inventorySlots.size() - 36];
            }
            if (containerSlotColor == null || containerSlotColor.length != container.inventorySlots.size() - 36) {
                containerSlotColor = new float[container.inventorySlots.size() - 36];
            }

            this.contents.add(new Content(() -> {
                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();

                boolean isEmpty = true;
                for (int i = 0; i < container.inventorySlots.size() - 36; ++i) {
                    Slot slot = (Slot) container.inventorySlots.get(i);

                    int x = slot.xDisplayPosition + 3;
                    int y = slot.yDisplayPosition - 10;

                    if (containerSlotColor[i] == 100) {
                        containerPrevSlotColor[i] = 0;
                    }

                    if (slot.getHasStack()) {
                        mc.getRenderItem().renderItemAndEffectIntoGUI(slot.getStack(), x - 2, y - 3);
                        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, slot.getStack(), x - 2, y - 3, null);
                        isEmpty = false;
                    }

                    RoundedUtils.drawRound(x - 2, y - 3, 16, 16, 7, new Color(220, 220, 220, (int) (containerSlotColor[i] = this.smooth(containerSlotColor[i], containerPrevSlotColor[i], 0.3f))));
                }

                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
                GlStateManager.popMatrix();

                if (isEmpty) {
                    descFont.drawCenteredString("Empty...", 90, (int) Math.ceil((float) container.getLowerChestInventory().getSizeInventory() / 9) * 10 - 3, -1);
                }
            }, 180, (int) Math.ceil((float) container.getLowerChestInventory().getSizeInventory() / 9) * 21, TOP));
        } else if (showContainerPreview && (mc.thePlayer.openContainer instanceof ContainerFurnace || mc.thePlayer.openContainer instanceof ContainerBrewingStand)) {
            final Container openContainer = mc.thePlayer.openContainer;
            this.contents.clear();

            if (containerPrevSlotColor == null || containerPrevSlotColor.length != openContainer.inventorySlots.size() - 36) {
                containerPrevSlotColor = new float[openContainer.inventorySlots.size() - 36];
            }
            if (containerSlotColor == null || containerSlotColor.length != openContainer.inventorySlots.size() - 36) {
                containerSlotColor = new float[openContainer.inventorySlots.size() - 36];
            }

            this.contents.add(new Content(() -> {
                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.enableDepth();

                boolean isEmpty = true;
                for (int i = 0; i < openContainer.inventorySlots.size() - 36; ++i) {
                    Slot slot = (Slot) openContainer.inventorySlots.get(i);

                    if (containerSlotColor[i] == 100) {
                        containerPrevSlotColor[i] = 0;
                    }

                    if (slot != null && slot.getHasStack()) {
                        mc.getRenderItem().renderItemAndEffectIntoGUI(slot.getStack(), 5 + 20 * i, 5);
                        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, slot.getStack(), 5 + 20 * i, 5, null);
                        isEmpty = false;
                    }

                    RoundedUtils.drawRound(3 + 20 * i, 3, 16, 16, 7, new Color(220, 220, 220, (int) (containerSlotColor[i] = this.smooth(containerSlotColor[i], containerPrevSlotColor[i], 0.3f))));
                }

                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableDepth();
                GlStateManager.popMatrix();

                if (isEmpty) {
                    descFont.drawCenteredString("Empty...", 90, 20, -1);
                }
            }, 180, 50, TOP));
        }

        final boolean showTab = this.tab.getValue() && mc.gameSettings.keyBindPlayerList.isKeyDown();

        if (showTab && mc.getNetHandler() != null) {
            final List<NetworkPlayerInfo> players = new ArrayList<>(mc.getNetHandler().getPlayerInfoMap());
            players.sort((a, b) -> {
                String nameA = a.getGameProfile() != null ? a.getGameProfile().getName() : "";
                String nameB = b.getGameProfile() != null ? b.getGameProfile().getName() : "";
                return nameA.compareToIgnoreCase(nameB);
            });

            final int rowsPerColumn = 15;
            final int columnCount = Math.max(1, (players.size() + rowsPerColumn - 1) / rowsPerColumn);
            final int displayRows = Math.min(players.size(), rowsPerColumn);
            final boolean showAvatar = this.playerAvatar.getValue();
            final float rowHeight = showAvatar ? 16f : 14f;
            final float headSize = 12f;
            final float headGap = 5f;
            final float headerHeight = 18f;
            final float paddingX = 8f;
            final float columnGap = 12f;
            final float pingWidth = descFont.getStringWidth("999ms") + 10f;
            final float contentHeight = headerHeight + displayRows * rowHeight + 6f;

            final float[] columnWidths = new float[columnCount];
            float totalWidth = 0f;
            for (int c = 0; c < columnCount; c++) {
                int start = c * rowsPerColumn;
                int end = Math.min(start + rowsPerColumn, players.size());
                float maxNameWidth = 0;
                for (int i = start; i < end; i++) {
                    NetworkPlayerInfo info = players.get(i);
                    String name = info.getGameProfile() != null ? info.getGameProfile().getName() : "";
                    maxNameWidth = Math.max(maxNameWidth, descFont.getStringWidth(name));
                }
                float headReserve = showAvatar ? headSize + headGap : 0f;
                columnWidths[c] = Math.max(140f, paddingX * 2 + headReserve + maxNameWidth + pingWidth + 10f);
                totalWidth += columnWidths[c];
                if (c > 0) totalWidth += columnGap;
            }
            final String onlineStr = players.size() + " online";
            final float headerMinWidth = paddingX + titleFont.getStringWidth("Player List") + 20 + descFont.getStringWidth(onlineStr) + paddingX;
            float contentWidth = Math.max(totalWidth, headerMinWidth);
            if (contentWidth > totalWidth) {
                columnWidths[columnCount - 1] += contentWidth - totalWidth;
            }

            this.contents.add(new Content(() -> {
                titleFont.drawString("Player List", paddingX, 5f, -1);
                descFont.drawString(onlineStr, contentWidth - paddingX - descFont.getStringWidth(onlineStr), 7f, new Color(160, 160, 160).getRGB());

                float columnX = 0f;
                for (int c = 0; c < columnCount; c++) {
                    int start = c * rowsPerColumn;
                    int end = Math.min(start + rowsPerColumn, players.size());
                    float colW = columnWidths[c];

                    for (int i = start; i < end; i++) {
                        NetworkPlayerInfo info = players.get(i);
                        String name = info.getGameProfile() != null ? info.getGameProfile().getName() : "?";
                        int ping = info.getResponseTime();
                        int rowInCol = i - start;
                        float rowY = headerHeight + rowInCol * rowHeight;
                        float headX = columnX + paddingX;
                        float headY = rowY + (rowHeight - headSize) / 2f - 1.5f;
                        float nameX = showAvatar ? headX + headSize + headGap : columnX + paddingX;

                        if (rowInCol % 2 == 0) {
                            RoundedUtils.drawRound(columnX + paddingX - 2, rowY - 1, colW - paddingX * 2 + 4, rowHeight - 2, 3f, new Color(255, 255, 255, 12));
                        }

                        if (showAvatar) {
                            net.minecraft.util.ResourceLocation skin = info.getLocationSkin();
                            if (skin != null) {
                                net.minecraft.client.renderer.texture.ITextureObject texObj = mc.getTextureManager().getTexture(skin);
                                if (texObj != null) {
                                    int texId = texObj.getGlTextureId();
                                    final float avatarRadius = 5f;
                                    final float faceU = 8f / 64f;
                                    final float faceV = 8f / 64f;
                                    final float faceSize = 8f / 64f;
                                    GlStateManager.color(1f, 1f, 1f, 1f);
                                    RoundedUtils.drawRoundTexturedUV(headX, headY, headSize, headSize, avatarRadius, 1f, texId, faceU, faceV, faceSize, faceSize);
                                    RoundedUtils.drawRoundTexturedUV(headX, headY, headSize, headSize, avatarRadius, 1f, texId, 40f / 64f, faceV, faceSize, faceSize);
                                    GlStateManager.color(1f, 1f, 1f, 1f);
                                }
                            }
                        }

                        int nameColor = mc.thePlayer != null && info.getGameProfile() != null
                                && info.getGameProfile().getId().equals(mc.thePlayer.getUniqueID())
                                ? new Color(120, 200, 255).getRGB() : -1;
                        descFont.drawString(name, nameX, rowY + (rowHeight - descFont.getHeight()) / 2f - 1.5f, nameColor);

                        String pingStr = ping <= 0 ? "..." : ping + "ms";
                        int pingColor;
                        if (ping <= 0) {
                            pingColor = new Color(160, 160, 160).getRGB();
                        } else if (ping < 100) {
                            pingColor = new Color(120, 220, 120).getRGB();
                        } else if (ping < 200) {
                            pingColor = new Color(220, 200, 120).getRGB();
                        } else {
                            pingColor = new Color(220, 120, 120).getRGB();
                        }
                        descFont.drawString(pingStr, columnX + colW - paddingX - descFont.getStringWidth(pingStr), rowY + (rowHeight - descFont.getHeight()) / 2f - 1.5f, pingColor);
                    }
                    columnX += colW + columnGap;
                }
            }, (int) contentWidth, (int) contentHeight, SUPREME));
        }

        int maxWeight = -1;
        for (Content content : this.contents) {
            maxWeight = Math.max(maxWeight, content.weight);
        }
        final int weight = maxWeight;
        this.contents.removeIf(content -> content.weight < weight);

        final NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        final String defaultText = "   " + mc.getSession().getUsername() + "   " + Minecraft.getDebugFPS() + "fps" + "   " + ((playerInfo == null || playerInfo.getResponseTime() == 0) ? "Pinging..." : playerInfo.getResponseTime() + " ping");

        final boolean idle = this.contents.isEmpty() && this.timedContents.isEmpty();
        final float targetWidth = idle
                ? 7 + titleFont.getStringWidth(CLIENT_NAME) + 4 + descFont.getStringWidth(defaultText) + 8
                : this.getMaxWidth(this.contents);
        final float targetHeight = idle
                ? Math.max(titleFont.getHeight(), descFont.getHeight()) + 8
                : this.getTotalHeight(this.contents);

        this.renderX = this.smooth(this.renderX, (sr.getScaledWidth() - this.width) / 2f, 0.2f);
        this.width = this.smooth(this.width, targetWidth, 0.2f);
        this.height = this.smooth(this.height, targetHeight, 0.2f);

        if (this.blur.getValue()) {
            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(this.renderX, 15, this.width, this.height, 10.5f, Color.BLACK);
            RenderUtil.resetColor();
            BlurUtils.blurEnd(2, 3.0F);
        }
        if (this.bloom.getValue()) {
            BlurUtils.prepareBloom();
            RoundedUtils.drawRound(this.renderX, 15, this.width, this.height, 10.5f, Color.BLACK);
            RenderUtil.resetColor();
            BlurUtils.bloomEnd(2, 3.0F);
        }
        RoundedUtils.drawRound(this.renderX, 15, this.width, this.height, 10.5f, new Color(0, 0, 0, 130));

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderUtil.scissor(this.renderX, 15, this.width, this.height);

        if (idle) {
            titleFont.drawString(CLIENT_NAME, this.renderX + 7, 15 + (this.height - titleFont.getHeight()) / 2f, Color.WHITE.getRGB());
            descFont.drawString(defaultText, this.renderX + 7 + titleFont.getStringWidth(CLIENT_NAME) + 4, 15 + (this.height - descFont.getHeight()) / 2f, -1);
        } else {
            float renderY = 15;
            for (Content content : new ArrayList<>(this.contents)) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(this.renderX, renderY, 0);
                this.currentContentY = renderY;
                content.content.run();
                GlStateManager.popMatrix();
                renderY += content.height;
            }
            this.contents.clear();
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || mc.thePlayer == null) return;

        if (event.getPacket() instanceof C0EPacketClickWindow && (mc.thePlayer.openContainer instanceof ContainerChest || mc.thePlayer.openContainer instanceof ContainerFurnace || mc.thePlayer.openContainer instanceof ContainerBrewingStand)) {
            C0EPacketClickWindow c0e = (C0EPacketClickWindow) event.getPacket();
            if (containerPrevSlotColor != null && containerPrevSlotColor.length == mc.thePlayer.openContainer.inventorySlots.size() - 36) {
                if (c0e.getSlotId() < mc.thePlayer.openContainer.inventorySlots.size() - 36 && c0e.getSlotId() >= 0) {
                    containerPrevSlotColor[c0e.getSlotId()] = 100;
                }
            }
        }

        if (event.getPacket() instanceof C0DPacketCloseWindow) {
            containerSlotColor = null;
            containerPrevSlotColor = null;
        }
    }

    private boolean isChestStealerEnabled() {
        ChestStealer chestStealer = (ChestStealer) Unfair.moduleManager.getModule(ChestStealer.class);
        return chestStealer != null && chestStealer.isEnabled();
    }

    public static boolean shouldHideChestGui() {
        DynamicIsland island = (DynamicIsland) Unfair.moduleManager.getModule(DynamicIsland.class);
        if (island == null || !island.isEnabled() || !island.containerPreview.getValue()) return false;
        ChestStealer chestStealer = (ChestStealer) Unfair.moduleManager.getModule(ChestStealer.class);
        if (chestStealer == null || !chestStealer.isEnabled()) return false;
        return mc.thePlayer != null && mc.currentScreen instanceof GuiChest;
    }

    public static boolean shouldHideTabList() {
        DynamicIsland island = (DynamicIsland) Unfair.moduleManager.getModule(DynamicIsland.class);
        if (island == null || !island.isEnabled() || !island.tab.getValue()) return false;
        return mc.thePlayer != null;
    }

    private void drawCheckIcon() {
        RenderUtil.drawLine(13, 20, 17, 24.5f, 2f, Color.WHITE.getRGB());
        RenderUtil.drawLine(17, 24.5f, 26, 14, 2f, Color.WHITE.getRGB());
    }

    private void drawWarnIcon() {
        RenderUtil.drawLine(19, 13, 12, 27, 1.5f, Color.WHITE.getRGB());
        RenderUtil.drawLine(19, 13, 26, 27, 1.5f, Color.WHITE.getRGB());
        RenderUtil.drawLine(12, 27, 26, 27, 1.5f, Color.WHITE.getRGB());
        RenderUtil.drawLine(19, 17, 19, 22, 1.5f, Color.WHITE.getRGB());
        RenderUtil.drawLine(19, 24, 19, 25.5f, 1.5f, Color.WHITE.getRGB());
    }

    private float smooth(float current, float target, float speed) {
        long deltaTime = this.frameDelta;

        speed = Math.abs(target - current) * speed;

        if (deltaTime < 1L) {
            deltaTime = 1L;
        }

        final float difference = current - target;
        final float smoothing = Math.max(speed * (deltaTime / 16F), .15F);

        if (difference > speed) {
            current = Math.max(current - smoothing, target);
        } else if (difference < -speed) {
            current = Math.min(current + smoothing, target);
        } else {
            current = target;
        }

        return current;
    }

    private float getMaxWidth(List<Content> contents) {
        float width = 0;
        for (Content content : contents) {
            if (width < content.width) {
                width = content.width;
            }
        }
        return width;
    }

    private float getTotalHeight(List<Content> contents) {
        float height = 0;
        for (Content content : contents) {
            height += content.height;
        }
        return height;
    }

    private static Color reAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public void addContent(Runnable content, int width, int height, int weight) {
        this.contents.add(new Content(content, width, height, weight));
    }

    public void addContent(ContentType type, String title, String text, long time) {
        this.timedContents.add(new TimedContent(type, title, text, time));
    }

    public void addContent(Module module) {
        for (TimedContent content : this.timedContents) {
            if (content.module == null) continue;
            if (content.module == module) {
                content.time = content.timer.getElapsedTime() + 2000;
                return;
            }
        }
        this.timedContents.add(new TimedContent(module));
    }

    public void addContent(Runnable icon, final String title, final String description, final float present, final Color color) {
        final UFontRenderer titleFont = Unfair.fontManager.getGoogleSansMedium(16);
        final UFontRenderer descFont = Unfair.fontManager.getGoogleSansMedium(14);
        this.contents.add(new Content(() -> {
            RoundedUtils.drawRound(7, 8, 24, 24, 7, reAlpha(color, 255));

            if (icon != null) {
                icon.run();
            }

            titleFont.drawString(title, 38, 9, -1);
            descFont.drawString(description, 38, 24, -1);
            RoundedUtils.drawRound(7, 38, this.width - 14, 5, 2, reAlpha(color.darker(), 150));
            RoundedUtils.drawRound(7, 38, Math.min(present * this.width - 14, this.width - 14), 5, 2, reAlpha(color, 255));
        }, 50 + Math.max(titleFont.getStringWidth(title), descFont.getStringWidth(description)), 50, MEDIUM));
    }

    public enum ContentType {
        MODULE,
        SUCCESS,
        WARNING,
        INFO
    }

    public static class Content {
        private final Runnable content;
        private final int width;
        private final int height;
        private final int weight;

        private Content(Runnable content, int width, int height, int weight) {
            this.content = content;
            this.width = width;
            this.height = height;
            this.weight = weight;
        }
    }

    private static class TimedContent {
        private final ContentType type;
        private final Module module;
        private final String title;
        private final EaseOutExpo animation;
        private final TimerUtil timer;
        private String description;
        private long time;

        TimedContent(ContentType contentType, String title, String description, long time) {
            this.type = contentType;
            this.module = null;
            this.title = title;
            this.description = description;
            this.animation = new EaseOutExpo(800, 1);
            this.timer = new TimerUtil();
            this.timer.reset();
            this.time = time;
        }

        TimedContent(Module module) {
            this.type = ContentType.MODULE;
            this.module = module;
            this.title = "Module Toggled";
            this.description = module.getName() + (module.isEnabled() ? " has been §2Enabled" : " has been §4Disabled");
            this.animation = new EaseOutExpo(300, 1);
            this.timer = new TimerUtil();
            this.timer.reset();
            this.time = 2000;
        }
    }

    private static class EaseOutExpo {
        private final int duration;
        private final double endPoint;
        private boolean forwards = true;
        private long lastMS = System.currentTimeMillis();

        EaseOutExpo(int ms, double endPoint) {
            this.duration = ms;
            this.endPoint = endPoint;
        }

        private long getTime() {
            return System.currentTimeMillis() - this.lastMS;
        }

        private boolean isDone() {
            return this.getTime() > this.duration;
        }

        void setDirection(boolean forwards) {
            if (this.forwards != forwards) {
                this.forwards = forwards;
                this.lastMS = System.currentTimeMillis() - (this.duration - Math.min(this.duration, this.getTime()));
            }
        }

        double getOutput() {
            if (this.forwards) {
                if (this.isDone()) {
                    return this.endPoint;
                }
                return this.getEquation(this.getTime() / (double) this.duration) * this.endPoint;
            }
            if (this.isDone()) {
                return 0.0;
            }
            return (1.0 - this.getEquation(this.getTime() / (double) this.duration)) * this.endPoint;
        }

        private double getEquation(double x) {
            return MathHelper.epsilonEquals((float) x, 1.0F) ? 1 : 1 - Math.pow(2, -10 * x);
        }
    }
}
