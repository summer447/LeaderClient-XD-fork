package unfair.events;

import net.minecraft.util.IChatComponent;
import unfair.event.events.callables.EventCancellable;

public class ChatReceivedEvent extends EventCancellable {
    private final byte type;
    private final IChatComponent message;

    public ChatReceivedEvent(byte type, IChatComponent message) {
        this.type = type;
        this.message = message;
    }

    public byte getType() {
        return this.type;
    }

    public IChatComponent getMessage() {
        return this.message;
    }
}
