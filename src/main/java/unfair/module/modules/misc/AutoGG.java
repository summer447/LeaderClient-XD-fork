package unfair.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S02PacketChat;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PacketEvent;
import unfair.events.TickEvent;
import unfair.module.Module;
import unfair.property.properties.IntProperty;
import unfair.property.properties.TextProperty;
import unfair.util.ChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * AutoGG：监听到游戏结束类聊天消息后，延迟随机发送一条 GG。
 * 触发词与发送内容均支持逗号分隔配置。
 */
public class AutoGG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    private final TextProperty triggers = new TextProperty(
            "Triggers",
            "won the game,game over,winner:,victory,1st killer,you earned,match over,game ended,defeat"
    );
    private final TextProperty messages = new TextProperty("Messages", "gg");
    private final IntProperty delay = new IntProperty("Delay Ticks", 20, 0, 200);

    private int pendingTicks = -1;
    private int cooldownTicks;

    public AutoGG() {
        super("AutoGG", false);
    }

    private static List<String> split(String raw) {
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }
        if (!(event.getPacket() instanceof S02PacketChat)) {
            return;
        }
        if (this.pendingTicks >= 0 || this.cooldownTicks > 0) {
            return;
        }

        String text = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String lower = text.toLowerCase();

        for (String trigger : split(this.triggers.getValue())) {
            if (lower.contains(trigger.toLowerCase())) {
                this.pendingTicks = this.delay.getValue();
                return;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
        }

        if (this.pendingTicks >= 0) {
            if (this.pendingTicks == 0) {
                List<String> pool = split(this.messages.getValue());
                if (!pool.isEmpty() && mc.thePlayer != null) {
                    ChatUtil.INSTANCE.sendMessage(pool.get(RANDOM.nextInt(pool.size())));
                }
                this.pendingTicks = -1;
                this.cooldownTicks = 100;
            } else {
                this.pendingTicks--;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.pendingTicks = -1;
        this.cooldownTicks = 0;
    }
}
