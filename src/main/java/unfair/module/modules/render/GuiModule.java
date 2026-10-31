package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.ModeProperty;
import unfair.ui.clickgui.dropdown.DropDownGui;
import unfair.ui.clickgui.oldgui.ClickGui;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Drop Down", "Old"});
    public final BooleanProperty blur = new BooleanProperty("blur", false, () -> mode.getValue() == 1);
    private DropDownGui dropDownGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (mode.getValue() == 0) {
            if (dropDownGui == null) dropDownGui = new DropDownGui();
            mc.displayGuiScreen(dropDownGui);
        }

        if (mode.getValue() == 1) {
            ClickGui clickGui = new ClickGui();
            if (!clickGui.isInitMain) clickGui.initMain();
            mc.displayGuiScreen(clickGui);
        }
    }
}
