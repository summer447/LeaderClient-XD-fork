package unfair.module.modules.player;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.event.types.Priority;
import unfair.events.*;
import unfair.management.RotationState;
import unfair.module.Module;
import unfair.module.modules.misc.BedNuker;
import unfair.module.modules.movement.LongJump;
import unfair.module.modules.render.HUD;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.ModeProperty;
import unfair.util.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
            0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };

    public static final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Telly", "Snap", "Extra", "GodBridge", "Breezily", "Vulcan GodBridge"});
    public final ModeProperty rotationMode = new ModeProperty("Rotate Mode", 3, new String[]{"None", "Vanilla", "Backwards", "Prediction"});
    public final ModeProperty towerMode = new ModeProperty("Tower Mode", 0, new String[]{"None", "Vanilla", "Motion", "Jump", "Extra", "Vulcan", "Karhu", "JumpExtra", "Old Hypixel"});
    public final ModeProperty rotationExtraMode = new ModeProperty("Rotate Extra Mode", 1, new String[]{"Telly", "Extra"}, () -> mode.getValue() == 3);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent"});
    public final IntProperty jumpDelay = new IntProperty("Jump Delay", 2, 0, 20, () -> mode.getValue() == 1);
    public final IntProperty placeDelay = new IntProperty("Place Delay", 1, 0, 5);
    public final IntProperty bridgeDelay = new IntProperty("Bridge Delay", 10, 0, 40, () -> mode.getValue() == 6);
    public final FloatProperty startRotSpeed = new FloatProperty("Start Rotate Speed", 180.0F, 40.0F, 180.0F);
    public final FloatProperty normalRotSpeed = new FloatProperty("Normal Rotate Speed", 180.0F, 40.0F, 180.0F);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("Item Spoof", false);
    public final BooleanProperty clutch = new BooleanProperty("Clutch", true, () -> mode.getValue() != 3);
    public final BooleanProperty onlyInVoid = new BooleanProperty("Only Void", false, () -> this.clutch.getValue() && mode.getValue() != 3);
    public final IntProperty forwardTicks = new IntProperty("Forward Ticks", 5, 1, 20, () -> mode.getValue() == 2);
    public final IntProperty backTicks = new IntProperty("Back Ticks", 5, 1, 20, () -> mode.getValue() == 2);

    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int tellyJumpDelayTimer = 0;
    private int jumpDelayOverride = -1;
    private boolean wasInAir = false;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private boolean clutchActive = false;
    private int clutchTickCounter = 0;
    private EnumFacing targetFacing = null;
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int towerTicks = 0;
    private double lastTowerY = 0.0;
    private int placeDelayCounter = 0;
    private boolean sa;
    private int snapTickCounter = 0;
    private boolean snapForward = true;
    private int extraTowerTick = 0;
    private int extraTowerDelay = 0;
    private boolean godbridgeSide = false;
    private float breezilySideways = 0.0F;
    private double breezilyEdge = 0.475D;
    private long breezilyLastAir = 0L;
    private boolean towerPulled = false;
    private int towerAirTicks = 0;
    private int bridgeGroundTicks = 0;

    public Scaffold() {
        super("Scaffold", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    private boolean shouldStopSprint() {
        if (this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.mode.getValue() == 6) {
            return false;
        }
        if (this.isTowering()) {
            return false;
        } else {
            return this.stage <= 0 && this.mode.getValue() != 2;
        }
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) Unfair.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!BlockUtil.isReplaceable(pos) && !BlockUtil.isInteractable(pos) && !(
                                mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) > (double) mc.playerController.getBlockReachDistance()
                        ) && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                if (facing != EnumFacing.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (BlockUtil.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                        )
                );
                BlockPos blockPos = positions.get(0);
                EnumFacing facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
            }
        }
    }

    private void handleBypass(UpdateEvent event, boolean tower) {
        boolean godbridge = this.mode.getValue() == 4 || this.mode.getValue() == 6;
        float[] rotations = this.bypassRotations(godbridge, tower, event);
        this.yaw = rotations[0];
        this.pitch = rotations[1];
        this.canRotate = true;

        event.setRotation(this.yaw, this.pitch, 3);
        if (this.moveFix.getValue() == 1) {
            event.setPervRotation(this.yaw, 3);
        }

        if (this.placeDelayCounter > 0) {
            this.placeDelayCounter--;
            return;
        }

        BlockData blockData = this.getBlockData();
        if (blockData == null) {
            return;
        }
        Vec3 hitVec = this.findBypassHitVec(blockData);
        if (hitVec != null && this.rotationTick <= 0) {
            this.place(blockData.blockPos(), blockData.facing(), hitVec);
            this.placeDelayCounter = this.placeDelay.getValue();
        }
    }

    private float[] bypassRotations(boolean godbridge, boolean tower, UpdateEvent event) {
        if (tower) {
            float backwards = MathHelper.wrapAngleTo180_float(this.getCurrentYaw() + 180.0F);
            return new float[]{RotationUtil.quantizeAngle(backwards + 45.0F), 83.0F};
        }

        if (!MoveUtil.isForwardPressed()) {
            float base = this.yaw != -180.0F ? this.yaw : event.getYaw();
            float noInputYaw = (float) (Math.floor(base / 90.0D) * 90.0D + 45.0D);
            return new float[]{noInputYaw, 75.0F};
        }

        float direction = MathHelper.wrapAngleTo180_float(this.getCurrentYaw() + 180.0F);
        float movingYaw = (float) Math.round(direction / 45.0F) * 45.0F;
        boolean straight = movingYaw % 90.0F == 0.0F;

        if (godbridge) {
            if (straight) {
                if (mc.thePlayer.onGround) {
                    double radians = Math.toRadians(movingYaw);
                    boolean side = Math.floor(mc.thePlayer.posX + Math.cos(radians) * 0.5D) != Math.floor(mc.thePlayer.posX)
                            || Math.floor(mc.thePlayer.posZ + Math.sin(radians) * 0.5D) != Math.floor(mc.thePlayer.posZ);

                    int feetY = MathHelper.floor_double(mc.thePlayer.posY);
                    int playerBX = MathHelper.floor_double(mc.thePlayer.posX);
                    int playerBZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    int nextBX = MathHelper.floor_double(mc.thePlayer.posX - Math.sin(radians) * 0.6D);
                    int nextBZ = MathHelper.floor_double(mc.thePlayer.posZ + Math.cos(radians) * 0.6D);

                    boolean leaningOff = BlockUtil.isReplaceable(new BlockPos(playerBX, feetY - 1, playerBZ));
                    boolean nextBlockAir = BlockUtil.isReplaceable(new BlockPos(nextBX, feetY - 1, nextBZ));
                    if (leaningOff && nextBlockAir) {
                        side = !side;
                    }
                    this.godbridgeSide = side;
                }
                return new float[]{movingYaw + (this.godbridgeSide ? 45.0F : -45.0F), 75.7F};
            }
            return new float[]{movingYaw, 75.6F};
        }

        if (straight) {
            return new float[]{movingYaw, 80.0F};
        }
        return new float[]{movingYaw, 75.6F};
    }

    private Vec3 findBypassHitVec(BlockData blockData) {
        float reach = mc.playerController.getBlockReachDistance();

        MovingObjectPosition exact = RotationUtil.rayTrace(this.yaw, this.pitch, reach, 1.0F);
        if (exact != null && exact.typeOfHit == MovingObjectType.BLOCK
                && exact.getBlockPos().equals(blockData.blockPos())
                && exact.sideHit == blockData.facing()) {
            return exact.hitVec;
        }

        double[] xs = placeOffsets;
        double[] ys = placeOffsets;
        double[] zs = placeOffsets;
        switch (blockData.facing()) {
            case NORTH:
                zs = new double[]{0.0D};
                break;
            case EAST:
                xs = new double[]{1.0D};
                break;
            case SOUTH:
                zs = new double[]{1.0D};
                break;
            case WEST:
            default:
                xs = new double[]{0.0D};
                break;
            case DOWN:
                ys = new double[]{0.0D};
                break;
            case UP:
                ys = new double[]{1.0D};
        }

        float bestScore = Float.MAX_VALUE;
        Vec3 bestHitVec = null;
        for (double dx : xs) {
            for (double dy : ys) {
                for (double dz : zs) {
                    double targetX = blockData.blockPos().getX() + dx;
                    double targetY = blockData.blockPos().getY() + dy;
                    double targetZ = blockData.blockPos().getZ() + dz;
                    float[] r = RotationUtil.getRotations(targetX, targetY, targetZ);
                    MovingObjectPosition mop = RotationUtil.rayTrace(r[0], r[1], reach, 1.0F);
                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(blockData.blockPos())
                            && mop.sideHit == blockData.facing()) {
                        float score = Math.abs(MathHelper.wrapAngleTo180_float(r[0] - this.yaw)) + Math.abs(r[1] - this.pitch);
                        if (score < bestScore) {
                            bestScore = score;
                            bestHitVec = mop.hitVec;
                        }
                    }
                }
            }
        }
        return bestHitVec;
    }

    private boolean isBlockBeneath() {
        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        return !BlockUtil.isReplaceable(below);
    }

    private void updateBreezilyInput() {
        if (mc.thePlayer.movementInput.moveForward <= 0.0F || mc.thePlayer.isSneaking()) {
            return;
        }

        int feetX = MathHelper.floor_double(mc.thePlayer.posX);
        int feetY = MathHelper.floor_double(mc.thePlayer.posY);
        int feetZ = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos below = new BlockPos(feetX, feetY - 1, feetZ);

        if (BlockUtil.isReplaceable(below)) {
            this.breezilyLastAir = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - this.breezilyLastAir > 500L) {
            return;
        }

        double modX = mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        double modZ = mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
        double far = 1.0D - this.breezilyEdge;
        float sideways = 0.0F;

        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        switch (facing) {
            case SOUTH:
                if (modX > far) sideways = 1.0F;
                if (modX < this.breezilyEdge) sideways = -1.0F;
                break;
            case NORTH:
                if (modX > far) sideways = -1.0F;
                if (modX < this.breezilyEdge) sideways = 1.0F;
                break;
            case EAST:
                if (modZ > far) sideways = -1.0F;
                if (modZ < this.breezilyEdge) sideways = 1.0F;
                break;
            case WEST:
            default:
                if (modZ > far) sideways = 1.0F;
                if (modZ < this.breezilyEdge) sideways = -1.0F;
                break;
        }

        if (sideways != this.breezilySideways && sideways != 0.0F) {
            this.breezilySideways = sideways;
            this.breezilyEdge = RandomUtil.nextDouble(0.45D, 0.5D);
        }
        mc.thePlayer.movementInput.moveStrafe = -this.breezilySideways;
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw,
                (float) MoveUtil.getForwardValue(),
                (float) MoveUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (!MoveUtil.isForwardPressed()) return false;
        if (PlayerUtil.isAirAbove()) return false;
        if (mc.thePlayer.onGround) {
            if (this.mode.getValue() == 3) {
                return this.stage > 0 || (this.towerMode.getValue() == 4 && mc.gameSettings.keyBindJump.isKeyDown());
            }
            if (this.stage > 0 || mc.gameSettings.keyBindJump.isKeyDown()) return true;
        }
        return this.tellyJumpDelayTimer > 0;
    }

    public int getSlot() {
        return this.lastSlot;
    }

    private void updateClutch() {
        if (!this.clutch.getValue()) {
            if (this.clutchActive) this.clutchReset();
            return;
        }
        if (mc.thePlayer.onGround) {
            if (this.clutchActive) {
                this.clutchReset();
            }
            return;
        }
        if (this.bbUnC()) {
            if (this.clutchActive) this.clutchReset();
            return;
        }
        double fallDistance = mc.thePlayer.fallDistance;
        boolean shouldClutch = fallDistance > 2 && !PlayerUtil.isAirAbove() && !mc.thePlayer.isCollidedHorizontally && (!this.onlyInVoid.getValue() || this.isFallingIntoVoid());
        if (shouldClutch && !this.clutchActive) {
            this.clutchActive = true;
            savedMotionX = mc.thePlayer.motionX;
            savedMotionY = mc.thePlayer.motionY;
            savedMotionZ = mc.thePlayer.motionZ;
            this.clutchTickCounter = 0;
        }
        if (this.clutchActive) {
            this.clutchTickCounter++;
            boolean isStuckPhase = this.clutchTickCounter % 10 != 0;
            if (isStuckPhase) {
                sa = true;
                if (this.clutchTickCounter % 10 == 1) {
                    savedMotionX = mc.thePlayer.motionX;
                    savedMotionY = mc.thePlayer.motionY;
                    savedMotionZ = mc.thePlayer.motionZ;
                }
                Unfair.moduleManager.getModule(Stuck.class).setEnabled(true);
            } else {
                sa = false;
                Unfair.moduleManager.getModule(Stuck.class).setEnabled(false);
            }
            if (clutchTickCounter >= 30) {
                clutchReset();
                clutchActive = false;
            }
        }
    }

    private void clutchReset() {
        if (this.clutchActive) {
            Unfair.moduleManager.getModule(Stuck.class).setEnabled(false);
        }
        this.clutchActive = false;
        this.clutchTickCounter = 0;
    }

    private boolean isFallingIntoVoid() {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i <= 128; i++) {
            BlockPos checkPos = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY) - i,
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private boolean bbUnC() {
        if (mc.thePlayer == null) return false;
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        for (int i = 1; i <= 2; i++) {
            BlockPos checkPos = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    playerY - i,
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) {
            return true;
        }
        double edgeThreshold = 0.15;
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < edgeThreshold || xOff > 1.0 - edgeThreshold || zOff < edgeThreshold || zOff > 1.0 - edgeThreshold) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < edgeThreshold ? -1 : (xOff > 1.0 - edgeThreshold ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < edgeThreshold ? -1 : (zOff > 1.0 - edgeThreshold ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) {
                    return true;
                }
            }
        }
        return false;
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }
    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            boolean tellyMode = this.mode.getValue() == 1;
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
                if (this.wasInAir) {
                    if (tellyMode) {
                        this.tellyJumpDelayTimer = this.jumpDelayOverride >= 0 ? this.jumpDelayOverride : this.jumpDelay.getValue();
                    } else {
                        this.tellyJumpDelayTimer = 0;
                    }
                    this.wasInAir = false;
                }
                if (this.tellyJumpDelayTimer > 0) this.tellyJumpDelayTimer--;
            } else {
                this.wasInAir = true;
            }
            if (tellyMode && mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) {
                this.stage = 1;
            }
            if (this.mode.getValue() == 3 && mc.thePlayer.onGround && this.stage == 0 && !mc.gameSettings.keyBindJump.isKeyDown()) {
                this.stage = 1;
            }
            if (tellyMode) {
                if (mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.jumpDelayOverride = 2;
                } else {
                    this.jumpDelayOverride = -1;
                }
            } else {
                this.jumpDelayOverride = -1;
                this.tellyJumpDelayTimer = 0;
            }
            this.updateClutch();
            if (this.mode.getValue() == 2) {
                if (mc.thePlayer.onGround && !this.isOnEdge()) {
                    int forward = forwardTicks.getValue();
                    int back = backTicks.getValue();
                    int cycle = forward + back;
                    if (cycle > 0) {
                        snapTickCounter++;
                        if (snapTickCounter >= cycle) {
                            snapTickCounter = 0;
                        }
                        snapForward = snapTickCounter < forward;
                    }
                } else {
                    snapForward = false;
                }
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                boolean bypassTechnique = this.mode.getValue() == 4 || this.mode.getValue() == 5 || this.mode.getValue() == 6;
                boolean bypassTower = this.towerMode.getValue() >= 5 && mc.gameSettings.keyBindJump.isKeyDown();
                if (bypassTechnique || bypassTower) {
                    this.handleBypass(event, bypassTower);
                    return;
                }
                if (this.mode.getValue() == 2) {
                    if (snapForward) {
                        this.yaw = RotationUtil.quantizeAngle(getCurrentYaw());
                        this.pitch = 80.0F;
                    } else {
                        this.yaw = RotationUtil.quantizeAngle(getCurrentYaw() + 180.0F);
                        this.pitch = 85.0F;
                    }
                    this.canRotate = true;
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw) ? yawDiffTo180 : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (this.mode.getValue() == 2 && snapForward) {
                    blockData = null;
                }
                if (blockData != null) {
                    if (this.rotationMode.getValue() == 3) {
                        double[] offsets = {0.1, 0.3, 0.5, 0.7, 0.9};
                        double[] x = offsets;
                        double[] y = offsets;
                        double[] z = offsets;
                        switch (blockData.facing()) {
                            case NORTH:
                                z = new double[]{0.02};
                                break;
                            case EAST:
                                x = new double[]{0.98};
                                break;
                            case SOUTH:
                                z = new double[]{0.98};
                                break;
                            case WEST:
                                x = new double[]{0.02};
                                break;
                            case DOWN:
                                y = new double[]{0.02};
                                break;
                            case UP:
                                y = new double[]{0.98};
                                break;
                        }
                        float bestYaw = -180.0F;
                        float bestPitch = 0.0F;
                        double bestDist = Double.MAX_VALUE;
                        Vec3 bestHitVec = null;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double targetX = blockData.blockPos().getX() + dx;
                                    double targetY = blockData.blockPos().getY() + dy;
                                    double targetZ = blockData.blockPos().getZ() + dz;
                                    float[] rot = RotationUtil.getRotations(targetX, targetY, targetZ);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                        float pitchDiff = Math.abs(rot[1] - this.pitch);
                                        double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                        if (dist < bestDist) {
                                            bestDist = dist;
                                            bestYaw = rot[0];
                                            bestPitch = rot[1];
                                            bestHitVec = mop.hitVec;
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            bestYaw += RandomUtil.nextFloat(-0.5F, 0.5F);
                            bestPitch += RandomUtil.nextFloat(-0.3F, 0.3F);
                            this.yaw = bestYaw;
                            this.pitch = bestPitch;
                            this.canRotate = true;
                            hitVec = bestHitVec;
                        }
                    } else {
                        double[] x = placeOffsets;
                        double[] y = placeOffsets;
                        double[] z = placeOffsets;
                        switch (blockData.facing()) {
                            case NORTH:
                                z = new double[]{0.0};
                                break;
                            case EAST:
                                x = new double[]{1.0};
                                break;
                            case SOUTH:
                                z = new double[]{1.0};
                                break;
                            case WEST:
                                x = new double[]{0.0};
                                break;
                            case DOWN:
                                y = new double[]{0.0};
                                break;
                            case UP:
                                y = new double[]{1.0};
                        }
                        float bestYaw = -180.0F;
                        float bestPitch = 0.0F;
                        float bestDiff = 0.0F;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                    double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                    double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                    float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                        float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                        if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                            bestYaw = rotations[0];
                                            bestPitch = rotations[1];
                                            bestDiff = totalDiff;
                                            hitVec = mop.hitVec;
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            this.yaw = bestYaw;
                            this.pitch = bestPitch;
                            this.canRotate = true;
                        }
                    }
                }
                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    if (this.rotationMode.getValue() == 2) {
                        this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    }
                }
                if (this.mode.getValue() == 3 && this.isTowering()) {
                    this.towering = true;
                }
                if (this.rotationMode.getValue() != 0 && this.mode.getValue() != 2) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.mode.getValue() == 3 && this.isTowering()) {
                        if (this.rotationExtraMode.getValue() != 1) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                            targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                            this.rotationTick = 3;
                            this.towering = true;
                        }
                    }
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2 ? startRotSpeed.getValue() : normalRotSpeed.getValue();
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (tellyMode && this.isTowering() && this.tellyJumpDelayTimer <= 0) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    } else if (tellyMode && this.tellyJumpDelayTimer > 0) {
                        targetYaw = this.yaw != -180.0F ? this.yaw : RotationUtil.quantizeAngle(
                                MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw()) + event.getYaw()
                        );
                        targetPitch = this.pitch > 10 || this.pitch < -10 ? this.pitch : 60.0F;
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                } else if (this.mode.getValue() == 2 && this.rotationMode.getValue() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = this.rotationTick >= 2 ? startRotSpeed.getValue() : normalRotSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) {
                        float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                        this.rotationTick = Math.max(this.rotationTick, 1);
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    if (this.placeDelayCounter > 0) {
                        this.placeDelayCounter--;
                    } else {
                        MovingObjectPosition finalCheck = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                        if (finalCheck != null && finalCheck.typeOfHit == MovingObjectType.BLOCK && finalCheck.getBlockPos().equals(blockData.blockPos()) && finalCheck.sideHit == blockData.facing()) {
                            this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        } else if (this.canRotate) {
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.mode.getValue() == 3 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        BlockData kyBlockData = this.getBlockData();
                        if (kyBlockData != null && this.rotationTick <= 0) {
                            Vec3 kyHitVec = BlockUtil.getHitVec(kyBlockData.blockPos(), kyBlockData.facing(), this.yaw, this.pitch);
                            this.place(kyBlockData.blockPos(), kyBlockData.facing(), kyHitVec);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onBypassTowerTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        boolean jumpHeld = mc.gameSettings.keyBindJump.isKeyDown();
        boolean vulcanAutoJump = this.mode.getValue() == 6 && !jumpHeld;
        int tower = vulcanAutoJump ? 5 : this.towerMode.getValue();
        if (tower < 5) {
            return;
        }

        if (mc.thePlayer.onGround) {
            this.towerAirTicks = 0;
            this.towerPulled = false;
            return;
        }
        this.towerAirTicks++;

        if ((!jumpHeld && !vulcanAutoJump)
                || !this.isBlockBeneath() || this.blockCount <= 0) {
            return;
        }

        switch (tower) {
            case 5:
                if (!this.towerPulled && mc.thePlayer.motionY < 0.1D) {
                    mc.thePlayer.motionY = -1.0D;
                    this.towerPulled = true;
                }
                break;
            case 6:
                if (!this.towerPulled && mc.thePlayer.motionY < 0.06D) {
                    mc.thePlayer.motionY -= 1.0D;
                    this.towerPulled = true;
                }
                break;
            case 7:
                if (this.towerAirTicks % 2 == 0) {
                    mc.thePlayer.motionY = 0.7D;
                } else {
                    mc.thePlayer.motionY = MoveUtil.isForwardPressed() ? 0.42D : 0.6D;
                }
                break;
            case 8:
                if (this.towerAirTicks > 14) {
                    mc.thePlayer.motionY -= 0.09D;
                    mc.thePlayer.motionX *= 0.6D;
                    mc.thePlayer.motionZ *= 0.6D;
                } else {
                    switch (this.towerAirTicks % 3) {
                        case 0:
                            mc.thePlayer.motionY = 0.42D;
                            if (MoveUtil.isForwardPressed()) {
                                float strafeSpeed = 0.247F - RandomUtil.nextFloat(0.0F, 0.01F);
                                MoveUtil.setSpeed(strafeSpeed, MoveUtil.getMoveYaw());
                            }
                            break;
                        case 2:
                            mc.thePlayer.motionY = 1.0D - mc.thePlayer.posY % 1.0D;
                            break;
                        default:
                            break;
                    }
                }
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
                event.setForward(0.0F);
                event.setStrafe(0.0F);
                return;
            }
            if (this.mode.getValue() == 2) return;
            boolean towerCondition = !mc.thePlayer.isCollidedHorizontally && mc.thePlayer.hurtTime <= 5 && !mc.thePlayer.isPotionActive(Potion.jump) && mc.gameSettings.keyBindJump.isKeyDown() && ItemUtil.isHoldingBlock();
            if (towerCondition) {
                if (this.towerMode.getValue() == 4) {
                    this.handleExtraTower(event);
                    return;
                }
                if (mc.thePlayer.onGround && this.tellyJumpDelayTimer <= 0 && PlayerUtil.isAirBelow()) {
                    this.handleTower(event);
                }
            } else if (this.towerMode.getValue() == 4) {
                this.extraTowerTick = 0;
                this.extraTowerDelay = 0;
            }
        }
    }

    private void handleTower(StrafeEvent event) {
        if (this.towerMode.getValue() == 0) {
            return;
        }
        this.startY = MathHelper.floor_double(mc.thePlayer.posY);
        this.towerTicks = 0;
        this.lastTowerY = mc.thePlayer.posY;
        switch (this.towerMode.getValue()) {
            case 1:
                mc.thePlayer.motionY = 0.42F;
                if (!MoveUtil.isForwardPressed()) {
                    MoveUtil.setSpeed(0.0);
                    event.setForward(0.0F);
                    event.setStrafe(0.0F);
                } else {
                    MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                }
                break;
            case 2:
                if (!MoveUtil.isForwardPressed() && MoveUtil.getSpeed() < 0.01) {
                    MoveUtil.setSpeed(0.005, MoveUtil.getMoveYaw());
                }
                break;
            case 3:
                if (!MoveUtil.isForwardPressed()) {
                    double microSpeed = 0.005;
                    MoveUtil.setSpeed(microSpeed, MoveUtil.getMoveYaw());
                } else {
                    MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                }
                break;
        }
    }
    private void handleExtraTower(StrafeEvent event) {
        int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
        switch (this.extraTowerTick) {
            case 0:
                if (mc.thePlayer.onGround) {
                    this.extraTowerTick = 1;
                    mc.thePlayer.motionY = -0.0784000015258789;
                }
                return;
            case 1:
                if (yState == 0 && PlayerUtil.isAirBelow()) {
                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                    if (!MoveUtil.isForwardPressed()) {
                        this.extraTowerDelay = 2;
                        MoveUtil.setSpeed(0.0);
                        event.setForward(0.0F);
                        event.setStrafe(0.0F);
                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                        double distance = this.distanceToEdge(facing);
                        if (distance > 0.1) {
                            if (mc.thePlayer.onGround) {
                                Vec3i directionVec = facing.getDirectionVec();
                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                AxisAlignedBB nextBox = mc.thePlayer
                                        .getEntityBoundingBox()
                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                    mc.thePlayer
                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                }
                                return;
                            }
                        } else {
                            this.extraTowerTick = 2;
                            this.targetFacing = facing;
                            mc.thePlayer.motionY = 0.42F;
                        }
                        return;
                    } else {
                        this.extraTowerTick = 2;
                        this.extraTowerDelay++;
                        mc.thePlayer.motionY = 0.42F;
                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                        return;
                    }
                } else {
                    this.extraTowerTick = 0;
                    this.extraTowerDelay = 0;
                    return;
                }
            case 2:
                this.extraTowerTick = 3;
                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                return;
            case 3:
                if (this.extraTowerDelay >= 4) {
                    this.extraTowerTick = 4;
                    this.extraTowerDelay = 0;
                } else {
                    this.extraTowerTick = 1;
                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                }
                return;
            case 4:
                this.extraTowerTick = 5;
                return;
            case 5:
                if (!PlayerUtil.isAirBelow()) {
                    this.extraTowerTick = 0;
                } else {
                    this.extraTowerTick = 1;
                    mc.thePlayer.motionY -= 0.08;
                    mc.thePlayer.motionY *= 0.98F;
                    mc.thePlayer.motionY -= 0.08;
                    mc.thePlayer.motionY *= 0.98F;
                }
                return;
            default:
                this.extraTowerTick = 0;
                this.extraTowerDelay = 0;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
                mc.thePlayer.movementInput.moveForward = 0.0F;
                mc.thePlayer.movementInput.moveStrafe = 0.0F;
                mc.thePlayer.movementInput.jump = false;
                mc.thePlayer.movementInput.sneak = false;
                return;
            }
            if ((this.mode.getValue() == 4 || this.mode.getValue() == 6)
                    && mc.thePlayer.onGround
                    && MoveUtil.isForwardPressed()
                    && !mc.gameSettings.keyBindJump.isKeyDown()
                    && this.isOnEdge()) {
                if (this.mode.getValue() != 6 || this.bridgeGroundTicks >= this.bridgeDelay.getValue()) {
                    mc.thePlayer.movementInput.jump = true;
                    this.bridgeGroundTicks = 0;
                }
            }
            if (this.mode.getValue() == 6 && mc.thePlayer.onGround) {
                this.bridgeGroundTicks++;
            }
            if (this.mode.getValue() == 5) {
                this.updateBreezilyInput();
            }
            if (this.moveFix.getValue() == 1 && RotationState.isActived() && RotationState.getPriority() == 3.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.mode.getValue() == 1 && mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.mode.getValue() == 3 && mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
                return;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            int count = 0;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0) {
                    Item item = stack.getItem();
                    if (item instanceof ItemBlock) {
                        Block block = ((ItemBlock) item).getBlock();
                        if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                            count += stack.stackSize;
                        }
                    }
                }
            }
            HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
            float scale = hud.scale.getValue();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 0.0F);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.fontRendererObj
                    .drawString(
                            String.format("%d block%s left", count, count != 1 ? "s" : ""),
                            ((float) new ScaledResolution(mc).getScaledWidth() / 2.0F + (float) mc.fontRendererObj.FONT_HEIGHT * 1.5F) / scale,
                            (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / scale - (float) mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                            (count > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | -1090519040,
                            true
                    );
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.lastSlot = event.setSlot(this.lastSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towering = false;
        this.towerTicks = 0;
        this.lastTowerY = 0.0;
        this.placeDelayCounter = 0;
        this.snapTickCounter = 0;
        this.snapForward = true;
        this.extraTowerTick = 0;
        this.extraTowerDelay = 0;
        this.godbridgeSide = false;
        this.breezilySideways = 0.0F;
        this.breezilyEdge = 0.475D;
        this.breezilyLastAir = 0L;
        this.towerPulled = false;
        this.towerAirTicks = 0;
        this.bridgeGroundTicks = 0;
    }

    @Override
    public void onDisabled() {
        this.clutchReset();
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
        this.snapTickCounter = 0;
        this.bridgeGroundTicks = 0;
        this.extraTowerTick = 0;
        this.extraTowerDelay = 0;
    }

    public double getLastTowerY() {
        return lastTowerY;
    }

    public void setLastTowerY(double lastTowerY) {
        this.lastTowerY = lastTowerY;
    }

    public int getTowerTicks() {
        return towerTicks;
    }

    public void setTowerTicks(int towerTicks) {
        this.towerTicks = towerTicks;
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}