package unfair.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S27PacketExplosion;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.MoveInputEvent;
import unfair.events.PacketEvent;
import unfair.events.UpdateEvent;
import unfair.mixin.IAccessorC03PacketPlayer;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.ModeProperty;
import unfair.property.properties.RangeProperty;
import unfair.util.KeyBindUtil;
import unfair.util.MoveUtil;
import unfair.util.PacketUtil;
import java.util.Random;

public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{
            "Vanilla", "Creative", "AirWalk", "Hypixel", "HypixelFlat"
    });
    public final FloatProperty horizontal = new FloatProperty("Horizontal", 0.44F, 0.1F, 5.0F, () -> this.mode.getValue() == 0);
    public final FloatProperty vertical = new FloatProperty("Vertical", 0.44F, 0.1F, 5.0F, () -> this.mode.getValue() == 0);
    public final RangeProperty glide = new RangeProperty("Glide", -0.04F, 0.0F, -1.0F, 1.0F, () -> this.mode.getValue() == 0);
    public final BooleanProperty bypassVanillaCheck = new BooleanProperty("Bypass Vanilla Check", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty sprintSpeedToggle = new BooleanProperty("Sprint Speed", true, () -> this.mode.getValue() == 0);
    public final FloatProperty sprintHorizontal = new FloatProperty("Sprint Horizontal", 1.0F, 0.1F, 5.0F,
            () -> this.mode.getValue() == 0 && this.sprintSpeedToggle.getValue());
    public final FloatProperty sprintVertical = new FloatProperty("Sprint Vertical", 1.0F, 0.1F, 5.0F,
            () -> this.mode.getValue() == 0 && this.sprintSpeedToggle.getValue());

    public final FloatProperty creativeSpeed = new FloatProperty("Creative Speed", 0.1F, 0.1F, 5.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty maxVelocity = new FloatProperty("Max Velocity", 4.0F, 1.0F, 20.0F, () -> this.mode.getValue() == 1);
    public final BooleanProperty creativeBypass = new BooleanProperty("Bypass Vanilla Check", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty forceFlight = new BooleanProperty("Force Flight", true, () -> this.mode.getValue() == 1);

    public final BooleanProperty airwalkOnGround = new BooleanProperty("Spoof On Ground", true, () -> this.mode.getValue() == 2);
    public final FloatProperty flatSpeed = new FloatProperty("Flat Speed", 1.66F, 0.8F, 2.0F, () -> this.mode.getValue() == 4);

    private boolean savedAllowFlying;
    private boolean vanillaBypassPending;
    private boolean creativeFlyDown;

    private boolean explosionTriggered;
    private int hypixelStep;
    private int flatTicks;

    public Fly() {
        super("Fly", false);
    }

    private void strafe(double speed) {
        MoveUtil.setSpeed(speed, MoveUtil.getMoveYaw());
    }

    private void handleVanilla() {
        boolean useSprint = this.sprintSpeedToggle.getValue() && mc.gameSettings.keyBindSprint.isKeyDown();
        float hs = useSprint ? this.sprintHorizontal.getValue() : this.horizontal.getValue();
        float vs = useSprint ? this.sprintVertical.getValue() : this.vertical.getValue();

        if (MoveUtil.isForwardPressed()) {
            this.strafe(hs);
        } else {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }

        if (this.bypassVanillaCheck.getValue()) {
            if (this.vanillaBypassPending) {
                mc.thePlayer.motionY = -0.04D;
                this.vanillaBypassPending = false;
                return;
            }
            if (mc.thePlayer.ticksExisted % 40 == 0) {
                this.vanillaBypassPending = true;
            }
        }

        if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.motionY = vs;
        } else if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            mc.thePlayer.motionY = -vs;
        } else {
            float glideLow = this.glide.getLow();
            float glideHigh = this.glide.getHigh();
            mc.thePlayer.motionY = glideHigh > glideLow
                    ? glideLow + RANDOM.nextFloat() * (glideHigh - glideLow)
                    : glideLow;
        }
    }

    private void handleCreative() {
        mc.thePlayer.capabilities.setFlySpeed(this.creativeSpeed.getValue());
        if (this.forceFlight.getValue()) {
            mc.thePlayer.capabilities.isFlying = true;
        }

        double mx = mc.thePlayer.motionX;
        double my = mc.thePlayer.motionY;
        double mz = mc.thePlayer.motionZ;
        double length = Math.sqrt(mx * mx + my * my + mz * mz);
        if (length > this.maxVelocity.getValue()) {
            double ratio = this.maxVelocity.getValue() / length;
            mc.thePlayer.motionX = mx * ratio;
            mc.thePlayer.motionY = my * ratio;
            mc.thePlayer.motionZ = mz * ratio;
        }

        if (this.creativeBypass.getValue() && mc.thePlayer.ticksExisted % 40 == 0
                && mc.theWorld.getCollidingBoundingBoxes(
                        mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, -0.55D, 0.0D)).isEmpty()) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX , mc.thePlayer.posY, mc.thePlayer.posZ, true));
            this.creativeFlyDown = true;
        }
    }

    private void handleAirwalk() {
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.onGround = this.airwalkOnGround.getValue();
    }

    private void handleHypixel() {
        switch (this.hypixelStep) {
            case 0:
                mc.thePlayer.motionY = 0.8D;
                break;
            case 1:
                this.strafe(1.9D);
                mc.thePlayer.motionY = 1.0D;
                break;
            case 2:
                mc.thePlayer.motionX *= 1.05D;
                mc.thePlayer.motionZ *= 1.05D;
                break;
            case 22:
                mc.thePlayer.motionY += 0.42D;
                break;
            default:
                break;
        }
        this.hypixelStep++;

        if (this.hypixelStep > 22 && mc.thePlayer.onGround) {
            this.setEnabled(false);
        }
    }

    private void handleHypixelFlat() {
        switch (this.hypixelStep) {
            case 0:
                this.strafe(0.8D);
                break;
            case 1:
                this.strafe(this.flatSpeed.getValue());
                break;
            default:
                if (this.flatTicks++ < 30 && !mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.0314D + RANDOM.nextDouble() / 1000.0D;
                    this.strafe(MoveUtil.getSpeed());
                }
                break;
        }
        this.hypixelStep++;

        if (mc.thePlayer.onGround && this.hypixelStep > 1) {
            this.setEnabled(false);
        }
    }

    // ================= 事件 =================

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.isRiding()) {
            return;
        }

        switch (this.mode.getValue()) {
            case 0:
                this.handleVanilla();
                break;
            case 1:
                this.handleCreative();
                break;
            case 2:
                this.handleAirwalk();
                break;
            case 3:
                if (this.explosionTriggered) {
                    this.handleHypixel();
                }
                break;
            case 4:
                if (this.explosionTriggered) {
                    this.handleHypixelFlat();
                }
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.mode.getValue() == 2) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
            Packet<?> packet = event.getPacket();

            if (this.mode.getValue() == 3 || this.mode.getValue() == 4) {

                if (packet instanceof S27PacketExplosion && ((S27PacketExplosion) packet).func_149144_d() != 0.0F) {
                    this.explosionTriggered = true;
                }
            }
            return;
        }

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer move = (C03PacketPlayer) packet;

        if (this.mode.getValue() == 2) {
            ((IAccessorC03PacketPlayer) move).setOnGround(this.airwalkOnGround.getValue());
        }

        if (this.mode.getValue() == 1 && this.creativeFlyDown && move instanceof C03PacketPlayer.C04PacketPlayerPosition) {
            event.setCancelled(true);
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                    mc.thePlayer.posX,
                    mc.thePlayer.prevPosY - 0.04D,
                    mc.thePlayer.posZ,
                    true
            ));
            this.creativeFlyDown = false;
        }
    }

    @Override
    public void onEnabled() {
        this.savedAllowFlying = mc.thePlayer.capabilities.allowFlying;
        if (this.mode.getValue() == 1) {
            mc.thePlayer.capabilities.allowFlying = true;
        }
    }

    @Override
    public void onDisabled() {
        mc.thePlayer.capabilities.allowFlying = this.savedAllowFlying;
        mc.thePlayer.capabilities.isFlying = false;
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
        this.vanillaBypassPending = false;
        this.creativeFlyDown = false;
        this.explosionTriggered = false;
        this.hypixelStep = 0;
        this.flatTicks = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
