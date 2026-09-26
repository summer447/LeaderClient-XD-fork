package unfair.module.modules.misc;

import unfair.event.EventManager;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.ModeProperty;
import unfair.property.properties.TextProperty;

public class ClientID extends Module {
    public static final ModeProperty mode = new ModeProperty("Client", 0, new String[] {"Vanilla", "Lunar", "Custom"});
    //public static final BooleanProperty modlist = new BooleanProperty("Server Look ModList", false);
    public static final TextProperty text = new TextProperty("Client Name", "" , () -> mode.getValue() == 2);
    public static Boolean isEnabled = false;
    public ClientID() {
        super("ClientID", false);
    }
    @Override
    public void onEnabled() {
        isEnabled = true;
        EventManager.register(this);
    }
    @Override
    public void onDisabled() {
        isEnabled = false;
        EventManager.unregister(this);
    }
    @Override
    public String[] getSuffix() {
        return new String[] {mode.getModeString()};
    }
}