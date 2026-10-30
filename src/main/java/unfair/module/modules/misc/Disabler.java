package unfair.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PacketEvent;
import unfair.events.PreMotionEvent;
import unfair.events.TickEvent;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.ModeProperty;
import unfair.util.ChatUtil;
import unfair.util.PacketUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static unfair.event.EventManager.call;

public class Disabler extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static final BooleanProperty customMode = new BooleanProperty("Custom Mode", false);
    public static final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"PredictionInventory"});
    private final List<Packet<?>> inventoryPackets = new ArrayList<>();
    private int forwardKeyReleaseTicks = 0;

    public static final CopyOnWriteArrayList<Packet<INetHandlerPlayClient>> storedPackets = new CopyOnWriteArrayList<>();

    public Disabler() {
        super("Disabler", false, false);
    }

    @Override
    public void onEnabled() {
        String currentMode = mode.getModeString();
        if (currentMode.equals("PredictionInventory")) {
            ChatUtil.sendFormatted(String.format("%s%s: You can use Vanilla-InvWalk & Silent-InvManager now",
                    Unfair.clientName, this.getName()));
        }
        resetStates();
    }
    public boolean checkCompass() {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i < 9; i++) {
            final ItemStack stackInSlot = mc.thePlayer.inventory.getStackInSlot(i);
            if (stackInSlot != null && stackInSlot.getUnlocalizedName().toLowerCase().contains("compass")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDisabled() {
        if (!inventoryPackets.isEmpty()) {
            for (Packet<?> p : inventoryPackets) {
                PacketUtil.sendPacketNoEvent(p);
            }
            inventoryPackets.clear();
        }
        storedPackets.clear();
        forwardKeyReleaseTicks = 0;
        resetStates();
    }

    private void resetStates() {
        inventoryPackets.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (forwardKeyReleaseTicks > 0) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            forwardKeyReleaseTicks--;
        }
    }
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        if (mode.getValue() == 0) {
            if (!this.checkCompass()) {
                if (event.getType() == EventType.SEND) {
                    handlePredictionInventory(event);
                }
            }
        }
    }
    @EventTarget
    public void onPre(PreMotionEvent event){
        if (this.isEnabled())releasePost();
    }
    private void handlePredictionInventory(PacketEvent event) {
        if (!mode.getModeString().equals("PredictionInventory")) return;

        Packet<?> packet = event.getPacket();

        if (mc.currentScreen instanceof GuiChest) return;

        if (packet instanceof C16PacketClientStatus) {
            event.setCancelled(true);
            inventoryPackets.add(packet);
        }

        else if (packet instanceof C0EPacketClickWindow) {
            event.setCancelled(true);
            inventoryPackets.add(packet);
        }
        else if (packet instanceof C0DPacketCloseWindow) {
            event.setCancelled(true);
            if (mc.thePlayer.isSprinting()) {
                PacketUtil.sendPacketNoEvent(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                forwardKeyReleaseTicks = 20;
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            }
            for (Packet<?> p : inventoryPackets) {
                if (!(p instanceof C16PacketClientStatus)) {
                    PacketUtil.sendPacketNoEvent(p);
                }
            }
            inventoryPackets.clear();
            PacketUtil.sendPacketNoEvent(packet);
        }
    }
    public static void releasePost() {
        if (!Unfair.moduleManager.getModule(Disabler.class).isEnabled() || mode.getValue() == 0) return;
        if (mc.getNetHandler() == null || storedPackets.isEmpty()) return;

        Iterator<Packet<INetHandlerPlayClient>> iterator = storedPackets.iterator();
        while (iterator.hasNext()) {
            Packet<INetHandlerPlayClient> packet = iterator.next();
            PacketEvent packetEvent = new PacketEvent(EventType.RECEIVE, packet);
            call(packetEvent);

            if (packetEvent.isCancelled()) {
                continue;
            }
            try {
                packet.processPacket(mc.getNetHandler());
            } catch (Exception ignored) {}
        }
        storedPackets.clear();
    }
}