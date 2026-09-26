package unfair.management.altmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import unfair.management.altmanager.microsoft.MicrosoftOAuthTranslation;
import unfair.ui.LeaderButton;
import unfair.ui.MaterialYou;
import unfair.util.shader.RoundedUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class AltManagerGui extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<Alt> alts = new ArrayList<>();
    public static String status = "§aIdle";
    private static File altFile;
    private int selectedAlt = -1;
    private int scrollOffset = 0;
    private final int maxVisibleAlts = 8;
    // UI dimensions
    private int guiX, guiY, guiWidth, guiHeight;
    private int listX, listY, listWidth, listHeight;
    private GuiTextField usernameField;

    public AltManagerGui() {
        loadAlts();
    }

    private static void loadAlts() {
        if (altFile == null) {
            altFile = new File(mc.mcDataDir, "unfair_alts.txt");
        }
        alts.clear();
        if (!altFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(altFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    boolean cracked = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                    Alt alt = new Alt(parts[0], parts[1], parts.length > 3 ? parts[3] : "", cracked);
                    if (parts.length > 4) alt.setRefreshToken(parts[4]);
                    if (parts.length > 5) alt.setBanned(Boolean.parseBoolean(parts[5]));
                    alts.add(alt);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveAlts() {
        if (altFile == null) {
            altFile = new File(mc.mcDataDir, "unfair_alts.txt");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(altFile))) {
            for (Alt alt : alts) {
                String line = alt.getEmail() + ":" + alt.getPassword() + ":" + alt.isCracked() + ":" +
                        (alt.getName() != null ? alt.getName() : "") + ":" +
                        (alt.getRefreshToken() != null ? alt.getRefreshToken() : "") + ":" + alt.isBanned();
                writer.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Alt> getAlts() {
        return alts;
    }

    public static void saveAltsStatic() {
        saveAlts();
    }

    @Override
    public void initGui() {
        ScaledResolution sr = new ScaledResolution(mc);
        guiWidth = 320;
        guiHeight = 240;
        guiX = (sr.getScaledWidth() - guiWidth) / 2;
        guiY = (sr.getScaledHeight() - guiHeight) / 2;

        listX = guiX + 10;
        listY = guiY + 30;
        listWidth = 150;
        listHeight = 160;

        this.buttonList.clear();
        int btnX = guiX + 170;
        int btnY = guiY + 30;
        int btnW = 140;
        int btnH = 20;
        int spacing = 26;

        this.buttonList.add(new LeaderButton(0, btnX, btnY, btnW, btnH, "Login"));
        this.buttonList.add(new LeaderButton(1, btnX, btnY + spacing, btnW, btnH, "Add Cracked"));
        this.buttonList.add(new LeaderButton(2, btnX, btnY + spacing * 2, btnW, btnH, "Token Login"));
        this.buttonList.add(new LeaderButton(3, btnX, btnY + spacing * 3, btnW, btnH, "Remove"));
        this.buttonList.add(new LeaderButton(4, btnX, btnY + spacing * 4, btnW, btnH, "OAuth Login"));
        this.buttonList.add(new LeaderButton(5, guiX + guiWidth / 2 - 30, guiY + guiHeight - 28, 60, btnH, "Back"));

        int inputX = guiX + 170;
        int inputY = guiY + 178;
        int inputW = 140;
        int inputH = 20;
        this.usernameField = new GuiTextField(0, this.fontRendererObj, inputX, inputY, inputW, inputH);
        this.usernameField.setMaxStringLength(64);
    }

    @Override
    public void drawDefaultBackground() {
        // 空实现，避免 MixinGuiScreen.drawBackground 重复绘制背景
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        try {
            MaterialYou.Scheme scheme = MaterialYou.getScheme();

            // Background
            Gui.drawRect(0, 0, this.width, this.height, scheme.getSurfaceContainer());

            // Main panel
            RoundedUtils.drawRound(guiX, guiY, guiWidth, guiHeight, 12, scheme.getSurfaceContainerHigh());

            // Title
            mc.fontRendererObj.drawString("Alt Manager", guiX + 16, guiY + 14, scheme.getPrimary());

            // Status
            mc.fontRendererObj.drawString("Status: " + status, guiX + 120, guiY + 14, scheme.getOnSurface());

            // Alt list background
            RoundedUtils.drawRound(listX, listY, listWidth, listHeight, 8, scheme.getSurfaceContainerHighest());

            // Alt list
            int itemHeight = 20;
            int visibleStart = Math.max(0, Math.min(scrollOffset, alts.size() - maxVisibleAlts));
            int visibleEnd = Math.min(visibleStart + maxVisibleAlts, alts.size());

            for (int i = visibleStart; i < visibleEnd; i++) {
                Alt alt = alts.get(i);
                int itemY = listY + 4 + (i - visibleStart) * itemHeight;
                boolean isSelected = i == selectedAlt;
                boolean isHovered = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= itemY && mouseY < itemY + itemHeight;

                int bgColor;
                int textColor;
                if (isSelected) {
                    bgColor = scheme.getPrimaryContainer();
                    textColor = scheme.getOnPrimaryContainer();
                } else if (isHovered) {
                    bgColor = MaterialYou.applyStateLayer(scheme.getSurfaceContainerHighest(), scheme.getOnSurface(), MaterialYou.STATE_HOVER);
                    textColor = scheme.getOnSurface();
                } else {
                    bgColor = 0;
                    textColor = scheme.getOnSurfaceVariant();
                }

                if (bgColor != 0) {
                    RoundedUtils.drawRound(listX + 4, itemY, listWidth - 8, itemHeight - 4, 6, bgColor);
                }

                String typeStr = alt.isCracked() ? "Cracked" : "Microsoft";
                String displayName = alt.getName() != null && !alt.getName().isEmpty() ? alt.getName() : alt.getEmail();
                if (displayName == null || displayName.isEmpty()) displayName = "Unknown";

                String listText = typeStr + ": " + displayName;
                if (alt.isBanned()) listText = "§c[Banned] §r" + listText;

                mc.fontRendererObj.drawString(listText, listX + 10, itemY + 6, textColor);
            }

            // Username label
            mc.fontRendererObj.drawString("Username/Token:", guiX + 170, guiY + 168, scheme.getOnSurfaceVariant());

            // Text field (themed by MixinGuiTextField)
            if (this.usernameField != null) {
                this.usernameField.drawTextBox();
            }

            // Buttons (themed by MaterialButtonRenderer via LeaderButton.drawButton)
            for (GuiButton button : this.buttonList) {
                button.drawButton(this.mc, mouseX, mouseY);
            }

            // Scroll info
            if (alts.size() > maxVisibleAlts) {
                mc.fontRendererObj.drawString("(" + alts.size() + " alts, scroll)", listX, listY + listHeight + 4, scheme.getOnSurfaceVariant());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Check alt list clicks
        int itemHeight = 20;
        for (int i = 0; i < alts.size(); i++) {
            int itemY = listY + 4 + i * itemHeight - scrollOffset * itemHeight;
            if (itemY >= listY && itemY < listY + listHeight && mouseX >= listX && mouseX <= listX + listWidth && mouseY >= itemY && mouseY < itemY + itemHeight) {
                selectedAlt = i;
                if (mouseButton == 0 && i < alts.size() && this.usernameField != null) {
                    Alt alt = alts.get(i);
                    this.usernameField.setText(alt.getName() != null ? alt.getName() : alt.getEmail());
                }
                return;
            }
        }

        // Text field
        if (this.usernameField != null) {
            this.usernameField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Buttons (vanilla handles via buttonList -> actionPerformed)
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: loginSelected(); break;
            case 1: addCracked(); break;
            case 2: mc.displayGuiScreen(new TokenLoginGui(this)); break;
            case 3: removeSelected(); break;
            case 4: startOAuth(); break;
            case 5: mc.displayGuiScreen(new GuiMainMenu()); break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(new GuiMainMenu());
            return;
        }

        if (this.usernameField != null) {
            this.usernameField.textboxKeyTyped(typedChar, keyCode);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0) {
            scrollOffset += scroll > 0 ? -1 : 1;
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, alts.size() - maxVisibleAlts)));
        }
    }

    private void loginSelected() {
        if (selectedAlt < 0 || selectedAlt >= alts.size()) {
            status = "§cNo alt selected";
            return;
        }
        Alt alt = alts.get(selectedAlt);
        if (alt.isCracked()) {
            SessionChanger.instance().loginCracked(alt.getEmail());
            status = "§aLogged in as " + alt.getEmail();
        } else {
            if (alt.hasRefreshToken()) {
                SessionChanger.instance().loginWithRefreshToken(alt.getRefreshToken());
            } else {
                SessionChanger.instance().loginMicrosoft(alt.getEmail(), alt.getPassword());
            }
        }
    }

    private void addCracked() {
        String username = this.usernameField.getText().trim();
        if (username.isEmpty()) {
            status = "§cEnter a username";
            return;
        }
        Alt alt = new Alt(username, "", username, true);
        alts.add(alt);
        saveAlts();
        this.usernameField.setText("");
        status = "§aAdded cracked alt";
    }

    private void removeSelected() {
        if (selectedAlt < 0 || selectedAlt >= alts.size()) {
            status = "§cNo alt selected";
            return;
        }
        alts.remove(selectedAlt);
        if (selectedAlt >= alts.size()) selectedAlt = alts.size() - 1;
        saveAlts();
        status = "§aRemoved alt";
    }

    private void startOAuth() {
        status = "§6Waiting for browser...";
        MicrosoftOAuthTranslation.getRefreshToken(token -> {
            if (token != null) {
                mc.addScheduledTask(() -> status = "§6Logging in...");

                // Login to get username first
                new Thread(() -> {
                    MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(token);
                    mc.addScheduledTask(() -> {
                        if (loginData.isGood()) {
                            // Create alt with real username
                            Alt alt = new Alt(loginData.username, "", loginData.username, false);
                            alt.setRefreshToken(token);
                            alt.setUuid(loginData.uuid);
                            alts.add(alt);
                            saveAlts();

                            // Set session
                            SessionChanger.instance().setSessionWithData(loginData);
                            status = "§aLogged in as " + loginData.username;
                        } else {
                            status = "§cOAuth login failed: " + (loginData.errorMessage != null ? loginData.errorMessage : "Unknown error");
                        }
                    });
                }).start();
            } else {
                mc.addScheduledTask(() -> status = "§cOAuth failed");
            }
        });
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
