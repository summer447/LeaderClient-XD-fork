package unfair.ui.clickgui.oldgui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import unfair.Unfair;
import unfair.config.Config;
import unfair.font.impl.UFontRenderer;
import unfair.module.modules.render.HUD;
import unfair.ui.clickgui.oldgui.ClickGui;
import unfair.ui.clickgui.oldgui.callback.GuiInput;
import unfair.util.ChatUtil;
import unfair.util.RenderUtil;
import unfair.util.Timer;

import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class ConfigCategoryComponent extends CategoryComponent {
    private static final int ROW_HEIGHT = 16;
    private static final int ENABLED_COLOR = new Color(24, 154, 255).getRGB();
    private static final int DISABLED_COLOR = new Color(192, 192, 192).getRGB();
    private static final int HOVER_COLOR = new Color(0, 0, 0, 120).getRGB();
    private static final FilenameFilter JSON_FILTER = (dir, name) -> name.endsWith(".json");

    private final List<ConfigEntry> configs = new ArrayList<>();
    private String currentConfig = "default.json";
    private int mouseX;
    private int mouseY;

    public ConfigCategoryComponent(String categoryName) {
        super(categoryName, new ArrayList<>());
        refreshConfigs();
    }

    public void refreshConfigs() {
        configs.clear();
        File dir = new File("./config/Unfair/");
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles(JSON_FILTER);
        if (files == null) return;
        for (File file : files) {
            configs.add(new ConfigEntry(file.getName()));
        }
    }

    @Override
    public void mousePosition(int mouseX, int mouseY) {
        super.mousePosition(mouseX, mouseY);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    protected int computeContentHeight() {
        if (!this.opened) return 0;
        int h = 0;
        for (ConfigEntry e : configs) {
            h += ROW_HEIGHT;
            if (e.expanded) h += e.actions().size() * ROW_HEIGHT;
        }
        this.bigSettings = h;
        int capped = (int) Math.min(h, this.screenHeight * 0.9d);
        this.big = capped;
        return capped;
    }

    @Override
    protected void renderContent() {
        if (!this.opened && this.smoothTimer == null) return;
        int rowY = this.y + this.titleHeight + 3;
        for (ConfigEntry e : configs) {
            drawRow(e.displayName(), rowY, e.name.equals(currentConfig) ? ENABLED_COLOR : DISABLED_COLOR);
            rowY += ROW_HEIGHT;
            if (e.expanded) {
                for (String action : e.actions()) {
                    drawRow(action, rowY, DISABLED_COLOR);
                    rowY += ROW_HEIGHT;
                }
            }
        }
    }

    private void drawRow(String text, int rowY, int color) {
        if (overRow(mouseX, mouseY, rowY)) {
            RenderUtil.drawRoundedRectangle(this.x, rowY, this.x + this.width, rowY + ROW_HEIGHT, 8, HOVER_COLOR);
        }
        if (HUD.font.getValue() == 0) {
            UFontRenderer fr = Unfair.fontManager.getFont(18);
            fr.drawString(text, this.x + this.width / 2.0F - fr.getStringWidth(text) / 2.0F, rowY + 4, color, true);
        } else {
            FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
            fr.drawString(text, this.x + this.width / 2.0F - fr.getStringWidth(text) / 2.0F, rowY + 4, color, true);
        }
    }

    private boolean overRow(int mx, int my, int rowY) {
        return mx > this.x && mx < this.x + this.width && my > rowY && my < rowY + ROW_HEIGHT;
    }

    public void mouseClicked(int mx, int my, int button) {
        int rowY = this.moduleY + this.titleHeight + 3;
        for (ConfigEntry e : new ArrayList<>(configs)) {
            if (overRow(mx, my, rowY)) {
                if (button == 0) {
                    loadConfig(e);
                } else if (button == 1) {
                    e.expanded = !e.expanded;
                    (this.smoothTimer = new Timer(200)).start();
                }
                return;
            }
            rowY += ROW_HEIGHT;
            if (e.expanded) {
                for (String action : e.actions()) {
                    if (overRow(mx, my, rowY)) {
                        if (button == 0) runAction(e, action);
                        return;
                    }
                    rowY += ROW_HEIGHT;
                }
            }
        }
    }

    private void loadConfig(ConfigEntry e) {
        new Config(e.name, false).load();
        currentConfig = e.name;
    }

    private void runAction(ConfigEntry e, String action) {
        switch (action) {
            case "Load":
                loadConfig(e);
                break;
            case "Save":
                new Config(e.name, true).save();
                currentConfig = e.name;
                break;
            case "Delete": {
                File file = new File("./config/Unfair/", e.name);
                if (file.delete()) {
                    configs.remove(e);
                    ChatUtil.sendFormatted("Deleted config: " + e.name);
                }
                break;
            }
            case "Rename":
                GuiInput.prompt("Rename", e.displayName(), input -> {
                    if (input != null) renameConfig(e, input);
                }, ClickGui.instance);
                break;
        }
    }

    private void renameConfig(ConfigEntry e, String input) {
        String newName = input.trim();
        if (newName.isEmpty()) return;
        if (!newName.endsWith(".json")) newName += ".json";
        if (newName.equalsIgnoreCase(e.name)) return;
        if (configExists(newName)) {
            ChatUtil.sendFormatted("Config already exists: " + newName);
            return;
        }
        File oldFile = new File("./config/Unfair/", e.name);
        File newFile = new File("./config/Unfair/", newName);
        if (oldFile.exists() && !newFile.exists() && oldFile.renameTo(newFile)) {
            if (currentConfig.equals(e.name)) currentConfig = newName;
            ChatUtil.sendFormatted("Renamed config: " + newName);
            refreshConfigs();
        }
    }

    private boolean configExists(String name) {
        for (ConfigEntry e : configs) {
            if (e.name.equals(name)) return true;
        }
        return false;
    }

    private static class ConfigEntry {
        final String name;
        boolean expanded;

        ConfigEntry(String name) {
            this.name = name;
        }

        String displayName() {
            return name.replace(".json", "");
        }

        List<String> actions() {
            List<String> list = new ArrayList<>();
            list.add("Load");
            list.add("Save");
            if (!"default.json".equals(name)) list.add("Delete");
            list.add("Rename");
            return list;
        }
    }
}
