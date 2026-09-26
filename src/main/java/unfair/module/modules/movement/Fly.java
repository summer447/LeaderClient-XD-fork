package unfair.module.modules.movement;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.MoveInputEvent;
import unfair.events.PacketEvent;
import unfair.events.UpdateEvent;
import unfair.mixin.IAccessorC03PacketPlayer;
import net.minecraft.util.BlockPos;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.ModeProperty;
import unfair.property.properties.RangeProperty;
import unfair.util.ChatUtil;
import unfair.util.KeyBindUtil;
import unfair.util.MoveUtil;
import unfair.util.PacketUtil;

import java.util.List;
import java.util.Random;

/**
 * 移植自 LiquidBounce ModuleFly。
 * 模式：Vanilla / Creative / Jetpack / AirWalk / Explosion / NCP / Hypixel / HypixelFlat。
 * LiquidBounce 依赖 Timer / BlockShape 事件，本客户端没有对应事件，相关部分用等价手段适配。
 */
public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{
            "Vanilla", "Creative", "Jetpack", "AirWalk", "Explosion", "NCP", "Hypixel", "HypixelFlat", "FakeBlock"
    });
    public final BooleanProperty disableOnSetback = new BooleanProperty("Disable On Setback", true);

    // ================= Vanilla =================
    public final FloatProperty horizontal = new FloatProperty("Horizontal", 0.44F, 0.1F, 5.0F, () -> this.mode.getValue() == 0);
    public final FloatProperty vertical = new FloatProperty("Vertical", 0.44F, 0.1F, 5.0F, () -> this.mode.getValue() == 0);
    public final RangeProperty glide = new RangeProperty("Glide", -0.04F, 0.0F, -1.0F, 1.0F, () -> this.mode.getValue() == 0);
    public final BooleanProperty bypassVanillaCheck = new BooleanProperty("Bypass Vanilla Check", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty sprintSpeedToggle = new BooleanProperty("Sprint Speed", true, () -> this.mode.getValue() == 0);
    public final FloatProperty sprintHorizontal = new FloatProperty("Sprint Horizontal", 1.0F, 0.1F, 5.0F,
            () -> this.mode.getValue() == 0 && this.sprintSpeedToggle.getValue());
    public final FloatProperty sprintVertical = new FloatProperty("Sprint Vertical", 1.0F, 0.1F, 5.0F,
            () -> this.mode.getValue() == 0 && this.sprintSpeedToggle.getValue());

    // ================= Creative =================
    public final FloatProperty creativeSpeed = new FloatProperty("Creative Speed", 0.1F, 0.1F, 5.0F, () -> this.mode.getValue() == 1);
    public final FloatProperty maxVelocity = new FloatProperty("Max Velocity", 4.0F, 1.0F, 20.0F, () -> this.mode.getValue() == 1);
    public final BooleanProperty creativeBypass = new BooleanProperty("Bypass Vanilla Check", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty forceFlight = new BooleanProperty("Force Flight", true, () -> this.mode.getValue() == 1);

    // ================= AirWalk =================
    public final BooleanProperty airwalkOnGround = new BooleanProperty("Spoof On Ground", true, () -> this.mode.getValue() == 3);

    // ================= Explosion =================
    public final FloatProperty explosionVertical = new FloatProperty("Explosion Vertical", 4.0F, 0.0F, 10.0F, () -> this.mode.getValue() == 4);
    public final FloatProperty startStrafe = new FloatProperty("Start Strafe", 1.0F, 0.6F, 4.0F, () -> this.mode.getValue() == 4);
    public final FloatProperty strafeDecrease = new FloatProperty("Strafe Decrease", 0.005F, 0.001F, 0.1F, () -> this.mode.getValue() == 4);

    // ================= NCP Clip =================
    public final FloatProperty ncpSpeed = new FloatProperty("NCP Speed", 0.8F, 0.2F, 7.5F, () -> this.mode.getValue() == 5);
    public final FloatProperty additionalEntry = new FloatProperty("Additional Entry", 0.2F, 0.0F, 2.0F, () -> this.mode.getValue() == 5);
    public final FloatProperty clipping = new FloatProperty("Clipping", -0.5F, -1.0F, 1.0F, () -> this.mode.getValue() == 5);
    public final FloatProperty maximumDistance = new FloatProperty("Maximum Distance", 200.0F, 10.0F, 500.0F, () -> this.mode.getValue() == 5);

    // ================= HypixelFlat =================
    public final FloatProperty flatSpeed = new FloatProperty("Flat Speed", 1.66F, 0.8F, 2.0F, () -> this.mode.getValue() == 7);

    // ================= 运行时状态 =================
    private boolean savedAllowFlying;
    private boolean vanillaBypassPending;
    private boolean creativeFlyDown;
    private BlockPos fakeBlockPos;
    private boolean fakeBlockUnderFeet;

    private float strafeSince;
    private int strafeDelay;

    private enum NcpState { WAIT_CEIL, CLIP_DONE, WAIT_AIR, FLY }
    private NcpState ncpState;
    private Vec3Pos ncpStart;

    private boolean explosionTriggered;
    private int hypixelStep;
    private int flatTicks;

    private static final class Vec3Pos {
        final double x, y, z;
        Vec3Pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public Fly() {
        super("Fly", false);
    }

    // ================= 工具 =================

    /** LiquidBounce withStrafe：按移动输入朝向设置水平速度 */
    private void strafe(double speed) {
        MoveUtil.setSpeed(speed, MoveUtil.getMoveYaw());
    }

    /** withStrafe()：保持当前速率，只修正方向 */
    private void keepStrafe() {
        this.strafe(MoveUtil.getSpeed());
    }

    private boolean collides(double yOffset) {
        List list = mc.theWorld.getCollidingBoundingBoxes(
                mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, yOffset, 0.0D)
        );
        return !list.isEmpty();
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(x, y, z, onGround));
    }

    /**
     * FakeBlock：在脚下模拟放置一个仅客户端可见的假方块（服务器无感知）。
     * 位置变化时自动清理旧方块并在新位置重新放置。
     */
    private void updateFakeBlock() {
        BlockPos under = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0D, mc.thePlayer.posZ);
        if (!under.equals(this.fakeBlockPos)) {
            this.clearFakeBlock();
            if (mc.theWorld.isAirBlock(under)) {
                this.placeFakeBlock(under);
            }
        }
        // 假方块正好垫在脚下时视为着地
        this.fakeBlockUnderFeet = this.fakeBlockPos != null && this.fakeBlockPos.equals(under);
    }

    /** 模拟真实放置：使用手上的方块，带挥臂动画和放置音效；手上没有方块则关闭模块 */
    private void placeFakeBlock(BlockPos pos) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            ChatUtil.INSTANCE.sendRaw("§c[Fly] No block in hand, disabling.");
            this.setEnabled(false);
            return;
        }
        Block block = ((ItemBlock) held.getItem()).block;

        mc.theWorld.setBlockState(pos, block.getStateFromMeta(held.getMetadata()));
        mc.thePlayer.swingItem();
        mc.theWorld.playSoundEffect(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                block.stepSound.getPlaceSound(),
                (block.stepSound.getVolume() + 1.0F) / 2.0F,
                block.stepSound.getFrequency() * 0.8F);
        this.fakeBlockPos = pos;
    }

    private void clearFakeBlock() {
        if (this.fakeBlockPos != null) {
            mc.theWorld.setBlockToAir(this.fakeBlockPos);
            this.fakeBlockPos = null;
        }
    }

    // ================= 模式行为 =================

    private void handleVanilla() {
        boolean useSprint = this.sprintSpeedToggle.getValue() && mc.gameSettings.keyBindSprint.isKeyDown();
        float hs = useSprint ? this.sprintHorizontal.getValue() : this.horizontal.getValue();
        float vs = useSprint ? this.sprintVertical.getValue() : this.vertical.getValue();

        // 没有移动输入时不产生水平位移
        if (MoveUtil.isForwardPressed()) {
            this.strafe(hs);
        } else {
            mc.thePlayer.motionX = 0.0D;
            mc.thePlayer.motionZ = 0.0D;
        }

        // 每 40 tick 下沉一次，绕过 vanilla fly kick
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
            // 在 Glide 区间内随机取值，两个把手均参与计算
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
            this.sendPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true);
            this.creativeFlyDown = true;
        }
    }

    private void handleJetpack() {
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.thePlayer.motionX *= 1.1D;
            mc.thePlayer.motionZ *= 1.1D;
            mc.thePlayer.motionY += 0.15D;
        }
    }

    private void handleAirwalk() {
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.onGround = this.airwalkOnGround.getValue();
    }

    private void handleExplosion() {
        if (this.strafeDelay > 0 && --this.strafeDelay == 0) {
            this.strafeSince = this.startStrafe.getValue();
        }
        if (this.strafeSince > 0.0F) {
            if (!mc.thePlayer.onGround) {
                this.strafe(this.strafeSince);
                this.strafeSince -= this.strafeDecrease.getValue();
            } else {
                this.strafeSince = 0.0F;
            }
        }
    }

    private void handleNcp() {
        switch (this.ncpState) {
            case WAIT_CEIL: {
                // 需要先站在不足 1 格的天花板下（头顶 +0.5 有碰撞）
                if (!this.collides(0.5D)) {
                    return;
                }
                if (this.clipping.getValue() != 0.0F) {
                    this.sendPos(mc.thePlayer.posX, mc.thePlayer.posY + this.clipping.getValue(), mc.thePlayer.posZ, false);
                    this.sendPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, false);
                }
                this.ncpState = NcpState.CLIP_DONE;
                return;
            }
            case CLIP_DONE: {
                // 等头顶碰撞消失后起跳
                if (this.collides(0.5D)) {
                    return;
                }
                mc.thePlayer.motionY = MoveUtil.getJumpMotion();
                this.strafe(this.ncpSpeed.getValue() + this.additionalEntry.getValue());
                this.ncpState = NcpState.WAIT_AIR;
                return;
            }
            case WAIT_AIR: {
                if (mc.thePlayer.onGround) {
                    return;
                }
                this.strafe(this.ncpSpeed.getValue());
                this.ncpState = NcpState.FLY;
                return;
            }
            case FLY: {
                // 触地即停
                if (this.collides(-0.4D)) {
                    this.setEnabled(false);
                    return;
                }
                double dx = mc.thePlayer.posX - this.ncpStart.x;
                double dy = mc.thePlayer.posY - this.ncpStart.y;
                double dz = mc.thePlayer.posZ - this.ncpStart.z;
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > this.maximumDistance.getValue()) {
                    ChatUtil.INSTANCE.sendRaw("§c[Fly] Maximum distance exceeded.");
                    this.setEnabled(false);
                    return;
                }
                this.keepStrafe();
            }
        }
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
                this.handleJetpack();
                break;
            case 3:
                this.handleAirwalk();
                break;
            case 4:
                this.handleExplosion();
                break;
            case 5:
                if (this.ncpState == null) {
                    this.ncpState = NcpState.WAIT_CEIL;
                    this.ncpStart = new Vec3Pos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                }
                this.handleNcp();
                break;
            case 6:
                if (this.explosionTriggered) {
                    this.handleHypixel();
                }
                break;
            case 7:
                if (this.explosionTriggered) {
                    this.handleHypixelFlat();
                }
                break;
            case 8:
                this.updateFakeBlock();
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
        // AirWalk：取消跳跃（此时 movementInput 刚更新，jump 还未被处理）
        if (this.mode.getValue() == 3) {
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

            // Setback：服务器回拉 = 已被标记
            if (packet instanceof S08PacketPlayerPosLook) {
                if (this.disableOnSetback.getValue()) {
                    ChatUtil.INSTANCE.sendRaw("§c[Fly] Setback detected, disabling.");
                    this.setEnabled(false);
                }
                return;
            }

            if (this.mode.getValue() == 6 || this.mode.getValue() == 7) {
                // Hypixel 模式由爆炸包触发
                if (packet instanceof S27PacketExplosion && ((S27PacketExplosion) packet).func_149144_d() != 0.0F) {
                    this.explosionTriggered = true;
                }
            }

            if (this.mode.getValue() == 4) {
                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
                    if (velocity.getEntityID() == mc.thePlayer.getEntityId()) {
                        event.setCancelled(true);
                        mc.thePlayer.motionX = 0.0D;
                        mc.thePlayer.motionZ = 0.0D;
                        mc.thePlayer.motionY = velocity.getMotionY() * this.explosionVertical.getValue();
                        this.strafeDelay = 1;
                    }
                } else if (packet instanceof S27PacketExplosion) {
                    S27PacketExplosion explosion = (S27PacketExplosion) packet;
                    if (explosion.func_149144_d() != 0.0F) {
                        event.setCancelled(true);
                        mc.thePlayer.motionX = 0.0D;
                        mc.thePlayer.motionZ = 0.0D;
                        mc.thePlayer.motionY = explosion.func_149144_d() * this.explosionVertical.getValue();
                        this.strafeDelay = 1;
                    }
                }
            }
            return;
        }

        // SEND：移动包修改
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof C03PacketPlayer)) {
            return;
        }
        C03PacketPlayer move = (C03PacketPlayer) packet;

        // AirWalk：移动包 onGround 标记
        if (this.mode.getValue() == 3) {
            ((IAccessorC03PacketPlayer) move).setOnGround(this.airwalkOnGround.getValue());
        }

        // FakeBlock：仅当假方块垫在脚下时标 onGround
        if (this.mode.getValue() == 8 && this.fakeBlockUnderFeet) {
            ((IAccessorC03PacketPlayer) move).setOnGround(true);
        }

        // Creative：下沉包的 Y 改为 lastTickPosY - 0.04
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
        if (this.mode.getValue() == 5) {
            this.ncpState = NcpState.WAIT_CEIL;
            this.ncpStart = new Vec3Pos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
        if (this.mode.getValue() == 4) {
            ChatUtil.INSTANCE.sendRaw("§e[Fly] You need to be hit by an explosion to fly.");
        }
    }

    @Override
    public void onDisabled() {
        // Creative：恢复能力
        mc.thePlayer.capabilities.allowFlying = this.savedAllowFlying;
        mc.thePlayer.capabilities.isFlying = false;

        // 停止水平运动
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionZ = 0.0D;

        // 清理客户端假方块
        this.clearFakeBlock();
        this.fakeBlockUnderFeet = false;

        this.vanillaBypassPending = false;
        this.creativeFlyDown = false;
        this.strafeSince = 0.0F;
        this.strafeDelay = 0;
        this.explosionTriggered = false;
        this.hypixelStep = 0;
        this.flatTicks = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
